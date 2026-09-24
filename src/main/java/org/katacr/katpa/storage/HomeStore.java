package org.katacr.katpa.storage;

import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.Home;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 持久化玩家个人家位置，支持 SQLite 单服和 MySQL 跨服共享。 */
public final class HomeStore {
    private final KaTpaPlugin plugin;
    private final ConcurrentMap<UUID, ConcurrentMap<String, Home>> homes = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<String, UUID>> nameIndex = new ConcurrentHashMap<>();
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "KaTpa-Home-Database");
        thread.setDaemon(true);
        return thread;
    });
    private JdbcConnectionManager connections;
    private boolean mysql;

    /** 创建绑定插件实例的家位置存储。 */
    public HomeStore(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 使用共享连接管理器初始化表结构。 */
    public void initialize(JdbcConnectionManager connections, boolean mysql) throws SQLException {
        this.connections = connections;
        this.mysql = mysql;
        connections.executeVoid(connection -> {
            try (var statement = connection.createStatement()) {
                if (mysql) {
                    statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS home (
                            id VARCHAR(36) PRIMARY KEY,
                            owner_id VARCHAR(36) NOT NULL,
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
                            description TEXT NOT NULL,
                            icon_material VARCHAR(64) NOT NULL DEFAULT '',
                            icon_custom_data INT,
                            icon_item_model VARCHAR(255) NOT NULL DEFAULT '',
                            created_at BIGINT NOT NULL
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
                } else {
                    statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS home (
                            id TEXT PRIMARY KEY,
                            owner_id TEXT NOT NULL,
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
                            description TEXT NOT NULL,
                            icon_material TEXT NOT NULL DEFAULT '',
                            icon_custom_data INTEGER,
                            icon_item_model TEXT NOT NULL DEFAULT '',
                            created_at INTEGER NOT NULL
                        )
                        """);
                }
            }
        });
    }

    /** 从数据库加载指定玩家的全部家到内存。 */
    public void load(UUID ownerId) throws SQLException {
        connections.executeVoid(connection -> {
            var loaded = new ConcurrentHashMap<String, Home>();
            var index = new ConcurrentHashMap<String, UUID>();
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT id, name, server, server_id, world, world_alias, x, y, z, yaw, pitch, description, " +
                            "icon_material, icon_custom_data, icon_item_model, created_at FROM home WHERE owner_id=?")) {
                stmt.setString(1, ownerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Home home = new Home(
                                UUID.fromString(rs.getString("id")),
                                ownerId, rs.getString("name"),
                                rs.getString("server"), rs.getString("server_id"), rs.getString("world"), rs.getString("world_alias"),
                                rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                                rs.getFloat("yaw"), rs.getFloat("pitch"),
                                rs.getString("description"),
                                rs.getString("icon_material"),
                                rs.getObject("icon_custom_data") == null ? null : rs.getInt("icon_custom_data"),
                                rs.getString("icon_item_model"),
                                rs.getLong("created_at"));
                        loaded.put(home.name().toLowerCase(Locale.ROOT), home);
                        index.put(home.name().toLowerCase(Locale.ROOT), home.id());
                    }
                }
            }
            homes.put(ownerId, loaded);
            nameIndex.put(ownerId, index);
        });
    }

    /** 保存或更新玩家的家，并异步持久化。 */
    public void save(Home home) {
        homes.computeIfAbsent(home.ownerId(), k -> new ConcurrentHashMap<>())
                .put(home.name().toLowerCase(Locale.ROOT), home);
        nameIndex.computeIfAbsent(home.ownerId(), k -> new ConcurrentHashMap<>())
                .put(home.name().toLowerCase(Locale.ROOT), home.id());
        executeUpdate(connection -> {
            String sql = mysql ? """
                    INSERT INTO home(id, owner_id, name, server, server_id, world, world_alias, x, y, z, yaw, pitch, description,
                        icon_material, icon_custom_data, icon_item_model, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE name=VALUES(name), server=VALUES(server), server_id=VALUES(server_id),
                        world=VALUES(world), world_alias=VALUES(world_alias),
                        x=VALUES(x), y=VALUES(y), z=VALUES(z), yaw=VALUES(yaw), pitch=VALUES(pitch),
                        description=VALUES(description), icon_material=VALUES(icon_material),
                        icon_custom_data=VALUES(icon_custom_data), icon_item_model=VALUES(icon_item_model)
                    """ : """
                    INSERT INTO home(id, owner_id, name, server, server_id, world, world_alias, x, y, z, yaw, pitch, description,
                        icon_material, icon_custom_data, icon_item_model, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET name=excluded.name, server=excluded.server, server_id=excluded.server_id,
                        world=excluded.world, world_alias=excluded.world_alias,
                        x=excluded.x, y=excluded.y, z=excluded.z, yaw=excluded.yaw, pitch=excluded.pitch,
                        description=excluded.description, icon_material=excluded.icon_material,
                        icon_custom_data=excluded.icon_custom_data, icon_item_model=excluded.icon_item_model
                    """;
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, home.id().toString());
                stmt.setString(2, home.ownerId().toString());
                stmt.setString(3, home.name());
                stmt.setString(4, home.server());
                stmt.setString(5, home.serverId() == null ? "" : home.serverId());
                stmt.setString(6, home.world());
                stmt.setString(7, home.worldAlias() == null ? "" : home.worldAlias());
                stmt.setDouble(8, home.x());
                stmt.setDouble(9, home.y());
                stmt.setDouble(10, home.z());
                stmt.setFloat(11, home.yaw());
                stmt.setFloat(12, home.pitch());
                stmt.setString(13, home.description() == null ? "" : home.description());
                stmt.setString(14, home.iconMaterial() == null ? "" : home.iconMaterial());
                if (home.iconCustomData() == null) {
                    stmt.setNull(15, java.sql.Types.INTEGER);
                } else {
                    stmt.setInt(15, home.iconCustomData());
                }
                stmt.setString(16, home.iconItemModel() == null ? "" : home.iconItemModel());
                stmt.setLong(17, home.createdAt());
                stmt.executeUpdate();
            }
        });
    }

    /** 删除玩家的指定家，并异步持久化。 */
    public void remove(UUID ownerId, String name) {
        var playerHomes = homes.get(ownerId);
        if (playerHomes != null) {
            Home home = playerHomes.remove(name.toLowerCase(Locale.ROOT));
            var index = nameIndex.get(ownerId);
            if (index != null && home != null) {
                index.remove(name.toLowerCase(Locale.ROOT));
            }
        }
        executeUpdate(connection -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM home WHERE owner_id=? AND name=?")) {
                stmt.setString(1, ownerId.toString());
                stmt.setString(2, name);
                stmt.executeUpdate();
            }
        });
    }

    /** 按名称查找玩家的家，大小写不敏感。 */
    public Home find(UUID ownerId, String name) {
        var playerHomes = homes.get(ownerId);
        return playerHomes == null ? null : playerHomes.get(name.toLowerCase(Locale.ROOT));
    }

    /** 返回玩家的全部家，按名称排序。 */
    public List<Home> all(UUID ownerId) {
        var playerHomes = homes.get(ownerId);
        if (playerHomes == null) return List.of();
        return playerHomes.values().stream()
                .sorted(Comparator.comparing(Home::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** 返回玩家全部家名称，按名称排序。 */
    public List<String> names(UUID ownerId) {
        return all(ownerId).stream().map(Home::name).toList();
    }

    /** 返回玩家的家数量。 */
    public int count(UUID ownerId) {
        var playerHomes = homes.get(ownerId);
        return playerHomes == null ? 0 : playerHomes.size();
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

    /** 将数据库写操作放入单线程队列并统一记录异常。 */
    private void executeUpdate(SqlOperation operation) {
        databaseExecutor.execute(() -> {
            try {
                connections.executeVoid(operation::run);
            } catch (SQLException e) {
                plugin.getLogger().severe("保存家位置失败: " + e.getMessage());
            }
        });
    }

    /** 表示一个可能抛出 SQL 异常的数据库写操作。 */
    @FunctionalInterface
    private interface SqlOperation {
        void run(Connection connection) throws SQLException;
    }
}
