package org.katacr.katpa.storage;

import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.Warp;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 持久化地标定义，支持 SQLite 单服和 MySQL 跨服共享。 */
public final class WarpStore {
    private final KaTpaPlugin plugin;
    private final ConcurrentMap<UUID, Warp> warps = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> nameIndex = new ConcurrentHashMap<>();
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "KaTpa-Warp-Database");
        thread.setDaemon(true);
        return thread;
    });
    private Connection connection;
    private boolean mysql;

    /** 创建绑定插件实例的地标存储。 */
    public WarpStore(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 使用共享数据库连接初始化表结构并加载全部地标。 */
    public void initialize(Connection sharedConnection, boolean mysql) throws SQLException {
        this.connection = sharedConnection;
        this.mysql = mysql;
        try (var statement = connection.createStatement()) {
            if (mysql) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS warp (
                            id VARCHAR(36) PRIMARY KEY,
                            name VARCHAR(64) NOT NULL,
                            server VARCHAR(64) NOT NULL,
                            world VARCHAR(128) NOT NULL,
                            x DOUBLE NOT NULL,
                            y DOUBLE NOT NULL,
                            z DOUBLE NOT NULL,
                            yaw FLOAT NOT NULL,
                            pitch FLOAT NOT NULL,
                            permission VARCHAR(128) NOT NULL DEFAULT '',
                            cooldown_seconds INT NOT NULL DEFAULT 0,
                            cost DOUBLE NOT NULL DEFAULT 0,
                            description TEXT NOT NULL DEFAULT '',
                            icon_material VARCHAR(64) NOT NULL DEFAULT '',
                            icon_custom_data INT,
                            icon_item_model VARCHAR(255) NOT NULL DEFAULT '',
                            created_at BIGINT NOT NULL,
                            updated_at BIGINT NOT NULL
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
            } else {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS warp (
                            id TEXT PRIMARY KEY,
                            name TEXT NOT NULL,
                            server TEXT NOT NULL,
                            world TEXT NOT NULL,
                            x REAL NOT NULL,
                            y REAL NOT NULL,
                            z REAL NOT NULL,
                            yaw REAL NOT NULL,
                            pitch REAL NOT NULL,
                            permission TEXT NOT NULL DEFAULT '',
                            cooldown_seconds INTEGER NOT NULL DEFAULT 0,
                            cost REAL NOT NULL DEFAULT 0,
                            description TEXT NOT NULL DEFAULT '',
                            icon_material TEXT NOT NULL DEFAULT '',
                            icon_custom_data INTEGER,
                            icon_item_model TEXT NOT NULL DEFAULT '',
                            created_at INTEGER NOT NULL,
                            updated_at INTEGER NOT NULL
                        )
                        """);
            }
        }
        loadAll();
    }

    /** 从数据库加载全部地标到内存。 */
    public void loadAll() throws SQLException {
        warps.clear();
        nameIndex.clear();
        try (var statement = connection.createStatement();
             var rs = statement.executeQuery(
                      "SELECT id, name, server, world, x, y, z, yaw, pitch, permission, " +
                              "cooldown_seconds, cost, description, icon_material, " +
                              "icon_custom_data, icon_item_model, created_at, updated_at FROM warp")) {
            while (rs.next()) {
                Warp warp = new Warp(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("name"),
                        rs.getString("server"),
                        rs.getString("world"),
                        rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                        rs.getFloat("yaw"), rs.getFloat("pitch"),
                        rs.getString("permission"),
                        rs.getInt("cooldown_seconds"),
                        rs.getDouble("cost"),
                        rs.getString("description"),
                        rs.getString("icon_material"),
                        rs.getObject("icon_custom_data") == null ? null : rs.getInt("icon_custom_data"),
                        rs.getString("icon_item_model"),
                        rs.getLong("created_at"),
                        rs.getLong("updated_at"));
                warps.put(warp.id(), warp);
                nameIndex.put(warp.name().toLowerCase(Locale.ROOT), warp.id());
            }
        }
    }

    /** 创建或更新地标，并异步持久化。 */
    public void save(Warp warp) {
        warps.put(warp.id(), warp);
        nameIndex.put(warp.name().toLowerCase(Locale.ROOT), warp.id());
        executeUpdate(() -> {
            String sql = mysql ? """
                    INSERT INTO warp(id, name, server, world, x, y, z, yaw, pitch, permission,
                        cooldown_seconds, cost, description, icon_material, icon_custom_data,
                        icon_item_model, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE name=VALUES(name), server=VALUES(server), world=VALUES(world),
                        x=VALUES(x), y=VALUES(y), z=VALUES(z), yaw=VALUES(yaw), pitch=VALUES(pitch),
                        permission=VALUES(permission), cooldown_seconds=VALUES(cooldown_seconds),
                        cost=VALUES(cost), description=VALUES(description), icon_material=VALUES(icon_material),
                        icon_custom_data=VALUES(icon_custom_data), icon_item_model=VALUES(icon_item_model),
                        updated_at=VALUES(updated_at)
                    """ : """
                    INSERT INTO warp(id, name, server, world, x, y, z, yaw, pitch, permission,
                        cooldown_seconds, cost, description, icon_material, icon_custom_data,
                        icon_item_model, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET name=excluded.name, server=excluded.server, world=excluded.world,
                        x=excluded.x, y=excluded.y, z=excluded.z, yaw=excluded.yaw, pitch=excluded.pitch,
                        permission=excluded.permission, cooldown_seconds=excluded.cooldown_seconds,
                        cost=excluded.cost, description=excluded.description, icon_material=excluded.icon_material,
                        icon_custom_data=excluded.icon_custom_data, icon_item_model=excluded.icon_item_model,
                        updated_at=excluded.updated_at
                    """;
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, warp.id().toString());
                stmt.setString(2, warp.name());
                stmt.setString(3, warp.server());
                stmt.setDouble(4, warp.x());
                stmt.setDouble(5, warp.y());
                stmt.setDouble(6, warp.z());
                stmt.setFloat(7, warp.yaw());
                stmt.setFloat(8, warp.pitch());
                stmt.setString(9, warp.permission());
                stmt.setInt(10, warp.cooldownSeconds());
                stmt.setDouble(11, warp.cost());
                stmt.setString(12, warp.description() == null ? "" : warp.description());
                stmt.setString(13, warp.iconMaterial() == null ? "" : warp.iconMaterial());
                if (warp.iconCustomData() == null) {
                    stmt.setNull(14, java.sql.Types.INTEGER);
                } else {
                    stmt.setInt(14, warp.iconCustomData());
                }
                stmt.setString(15, warp.iconItemModel() == null ? "" : warp.iconItemModel());
                stmt.setLong(16, warp.createdAt());
                stmt.setLong(17, warp.updatedAt());
                stmt.executeUpdate();
            }
        });
    }

    /** 删除地标，并异步持久化。 */
    public void remove(String name) {
        Warp warp = find(name);
        if (warp != null) {
            warps.remove(warp.id());
            nameIndex.remove(name.toLowerCase(Locale.ROOT));
        }
        executeUpdate(() -> {
            try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM warp WHERE name=?")) {
                stmt.setString(1, name);
                stmt.executeUpdate();
            }
        });
    }

    /** 按名称查找地标，大小写不敏感。 */
    public Warp find(String name) {
        UUID id = nameIndex.get(name.toLowerCase(Locale.ROOT));
        return id == null ? null : warps.get(id);
    }

    /** 按 ID 查找地标。 */
    public Warp find(UUID id) {
        return warps.get(id);
    }

    /** 重命名地标：复制全部字段到新名称后删除旧记录，返回新 Warp。 */
    public Warp rename(Warp warp, String newName, long now) {
        remove(warp.name());
        Warp renamed = new Warp(warp.id(), newName, warp.server(), warp.world(),
                warp.x(), warp.y(), warp.z(), warp.yaw(), warp.pitch(),
                warp.permission(), warp.cooldownSeconds(), warp.cost(),
                warp.description(), warp.iconMaterial(), warp.iconCustomData(), warp.iconItemModel(),
                warp.createdAt(), now);
        save(renamed);
        return renamed;
    }

    /** 返回全部地标，按名称排序。 */
    public List<Warp> all() {
        return warps.values().stream()
                .sorted(Comparator.comparing(Warp::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** 返回全部地标名称，按名称排序。 */
    public List<String> names() {
        return all().stream().map(Warp::name).toList();
    }

    /** 等待异步写入完成。连接由 SettingsStore 管理。 */
    public void close() {
        databaseExecutor.shutdown();
        try {
            if (!databaseExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                databaseExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            databaseExecutor.shutdownNow();
        }
    }

    /** 将数据库写操作放入单线程队列并统一记录异常。 */
    private void executeUpdate(SqlOperation operation) {
        databaseExecutor.execute(() -> {
            try {
                operation.run();
            } catch (SQLException e) {
                plugin.getLogger().severe("保存地标失败: " + e.getMessage());
            }
        });
    }

    /** 表示一个可能抛出 SQL 异常的数据库写操作。 */
    @FunctionalInterface
    private interface SqlOperation {
        /** 执行具体 SQL 写入。 */
        void run() throws SQLException;
    }
}
