package org.katacr.katpa.storage;

import org.bukkit.Bukkit;
import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.Warp;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
    /** 数据变更广播主题，供其他子服刷新缓存。 */
    private static final String SYNC_TOPIC = "warp";
    private final KaTpaPlugin plugin;
    private final ConcurrentMap<UUID, Warp> warps = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> nameIndex = new ConcurrentHashMap<>();
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "KaTpa-Warp-Database");
        thread.setDaemon(true);
        return thread;
    });
    private JdbcConnectionManager connections;
    private boolean mysql;

    /** 创建绑定插件实例的地标存储。 */
    public WarpStore(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 使用共享连接管理器初始化表结构并加载全部地标。 */
    public void initialize(JdbcConnectionManager connections, boolean mysql) throws SQLException {
        this.connections = connections;
        this.mysql = mysql;
        connections.executeVoid(connection -> {
            try (var statement = connection.createStatement()) {
                if (mysql) {
                    statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS warp (
                            id VARCHAR(36) PRIMARY KEY,
                            name VARCHAR(64) NOT NULL,
                            server VARCHAR(64) NOT NULL,
                            server_id VARCHAR(64) NOT NULL DEFAULT '',
                            world VARCHAR(128) NOT NULL,
                            world_alias VARCHAR(128) NOT NULL DEFAULT '',
                            x DOUBLE NOT NULL,
                            y DOUBLE NOT NULL,
                            z DOUBLE NOT NULL,
                            yaw FLOAT NOT NULL,
                            pitch FLOAT NOT NULL,
                            permission VARCHAR(128) NOT NULL DEFAULT '',
                            cooldown_seconds INT NOT NULL DEFAULT 0,
                            cost DOUBLE NOT NULL DEFAULT 0,
                            description TEXT NOT NULL,
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
                            server_id TEXT NOT NULL DEFAULT '',
                            world TEXT NOT NULL,
                            world_alias TEXT NOT NULL DEFAULT '',
                            x REAL NOT NULL,
                            y REAL NOT NULL,
                            z REAL NOT NULL,
                            yaw REAL NOT NULL,
                            pitch REAL NOT NULL,
                            permission TEXT NOT NULL DEFAULT '',
                            cooldown_seconds INTEGER NOT NULL DEFAULT 0,
                            cost REAL NOT NULL DEFAULT 0,
                            description TEXT NOT NULL,
                            icon_material TEXT NOT NULL DEFAULT '',
                            icon_custom_data INTEGER,
                            icon_item_model TEXT NOT NULL DEFAULT '',
                            created_at INTEGER NOT NULL,
                            updated_at INTEGER NOT NULL
                        )
                        """);
                }
            }
        });
        loadAll();
    }

    /** 从数据库加载全部地标到内存。 */
    public void loadAll() throws SQLException {
        applyLoaded(readAll());
    }

    /** 异步从数据库重载全部地标到内存（用于跨服变更后刷新本地缓存）。 */
    public void reload() {
        databaseExecutor.execute(() -> {
            Loaded loaded;
            try {
                loaded = readAll();
            } catch (SQLException e) {
                plugin.getLogger().warning("刷新地标缓存失败: " + e.getMessage());
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> applyLoaded(loaded));
        });
    }

    /** 读取全表到局部映射，不触碰共享缓存，供主线程安全应用。 */
    private Loaded readAll() throws SQLException {
        return connections.execute(connection -> {
            Map<UUID, Warp> loadedWarps = new HashMap<>();
            Map<String, UUID> loadedNames = new HashMap<>();
            try (var statement = connection.createStatement();
                 var rs = statement.executeQuery(
                           "SELECT id, name, server, server_id, world, world_alias, x, y, z, yaw, pitch, permission, " +
                                   "cooldown_seconds, cost, description, icon_material, " +
                                   "icon_custom_data, icon_item_model, created_at, updated_at FROM warp")) {
                while (rs.next()) {
                    Warp warp = new Warp(
                            UUID.fromString(rs.getString("id")),
                            rs.getString("name"),
                            rs.getString("server"),
                            rs.getString("server_id"),
                            rs.getString("world"),
                            rs.getString("world_alias"),
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
                    loadedWarps.put(warp.id(), warp);
                    loadedNames.put(warp.name().toLowerCase(Locale.ROOT), warp.id());
                }
            }
            return new Loaded(loadedWarps, loadedNames);
        });
    }

    /** 用最新快照合并共享缓存：先补入新值再移除已删除项，避免读取方看到空列表。 */
    private void applyLoaded(Loaded loaded) {
        warps.putAll(loaded.warps());
        warps.keySet().retainAll(loaded.warps().keySet());
        nameIndex.putAll(loaded.names());
        nameIndex.keySet().retainAll(loaded.names().keySet());
    }

    /** 一次全量读取的结果快照。 */
    private record Loaded(Map<UUID, Warp> warps, Map<String, UUID> names) {
    }

    /** 创建或更新地标，并异步持久化。 */
    public void save(Warp warp) {
        warps.put(warp.id(), warp);
        nameIndex.put(warp.name().toLowerCase(Locale.ROOT), warp.id());
        executeUpdate(connection -> {
            String sql = mysql ? """
                    INSERT INTO warp(id, name, server, server_id, world, world_alias, x, y, z, yaw, pitch, permission,
                        cooldown_seconds, cost, description, icon_material, icon_custom_data,
                        icon_item_model, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE name=VALUES(name), server=VALUES(server), server_id=VALUES(server_id),
                        world=VALUES(world), world_alias=VALUES(world_alias),
                        x=VALUES(x), y=VALUES(y), z=VALUES(z), yaw=VALUES(yaw), pitch=VALUES(pitch),
                        permission=VALUES(permission), cooldown_seconds=VALUES(cooldown_seconds),
                        cost=VALUES(cost), description=VALUES(description), icon_material=VALUES(icon_material),
                        icon_custom_data=VALUES(icon_custom_data), icon_item_model=VALUES(icon_item_model),
                        updated_at=VALUES(updated_at)
                    """ : """
                    INSERT INTO warp(id, name, server, server_id, world, world_alias, x, y, z, yaw, pitch, permission,
                        cooldown_seconds, cost, description, icon_material, icon_custom_data,
                        icon_item_model, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET name=excluded.name, server=excluded.server, server_id=excluded.server_id,
                        world=excluded.world, world_alias=excluded.world_alias,
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
                stmt.setString(4, warp.serverId() == null ? "" : warp.serverId());
                stmt.setString(5, warp.world());
                stmt.setString(6, warp.worldAlias() == null ? "" : warp.worldAlias());
                stmt.setDouble(7, warp.x());
                stmt.setDouble(8, warp.y());
                stmt.setDouble(9, warp.z());
                stmt.setFloat(10, warp.yaw());
                stmt.setFloat(11, warp.pitch());
                stmt.setString(12, warp.permission());
                stmt.setInt(13, warp.cooldownSeconds());
                stmt.setDouble(14, warp.cost());
                stmt.setString(15, warp.description() == null ? "" : warp.description());
                stmt.setString(16, warp.iconMaterial() == null ? "" : warp.iconMaterial());
                if (warp.iconCustomData() == null) {
                    stmt.setNull(17, java.sql.Types.INTEGER);
                } else {
                    stmt.setInt(17, warp.iconCustomData());
                }
                stmt.setString(18, warp.iconItemModel() == null ? "" : warp.iconItemModel());
                stmt.setLong(19, warp.createdAt());
                stmt.setLong(20, warp.updatedAt());
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
        executeUpdate(connection -> {
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
        Warp renamed = new Warp(warp.id(), newName, warp.server(), warp.serverId(), warp.world(), warp.worldAlias(),
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

    /** 等待异步写入完成。物理连接由 SettingsStore 管理。 */
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

    /** 将数据库写操作放入单线程队列并统一记录异常；写入成功后广播变更通知其他子服刷新。 */
    private void executeUpdate(SqlOperation operation) {
        databaseExecutor.execute(() -> {
            try {
                connections.executeVoid(operation::run);
            } catch (SQLException e) {
                plugin.getLogger().severe("保存地标失败: " + e.getMessage());
                return;
            }
            if (plugin.network() != null) {
                plugin.network().notifyDataChanged(SYNC_TOPIC);
            }
        });
    }

    /** 表示一个可能抛出 SQL 异常的数据库写操作。 */
    @FunctionalInterface
    private interface SqlOperation {
        /** 执行具体 SQL 写入。 */
        void run(Connection connection) throws SQLException;
    }
}
