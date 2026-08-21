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
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "KaTpa-Home-Database");
        thread.setDaemon(true);
        return thread;
    });
    private Connection connection;
    private boolean mysql;

    /** 创建绑定插件实例的家位置存储。 */
    public HomeStore(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 使用共享数据库连接初始化表结构。 */
    public void initialize(Connection sharedConnection, boolean mysql) throws SQLException {
        this.connection = sharedConnection;
        this.mysql = mysql;
        try (var statement = connection.createStatement()) {
            if (mysql) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS home (
                            owner_id VARCHAR(36) NOT NULL,
                            name VARCHAR(64) NOT NULL,
                            server VARCHAR(64) NOT NULL,
                            world VARCHAR(128) NOT NULL,
                            x DOUBLE NOT NULL,
                            y DOUBLE NOT NULL,
                            z DOUBLE NOT NULL,
                            yaw FLOAT NOT NULL,
                            pitch FLOAT NOT NULL,
                            created_at BIGINT NOT NULL,
                            PRIMARY KEY (owner_id, name)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
            } else {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS home (
                            owner_id TEXT NOT NULL,
                            name TEXT NOT NULL,
                            server TEXT NOT NULL,
                            world TEXT NOT NULL,
                            x REAL NOT NULL,
                            y REAL NOT NULL,
                            z REAL NOT NULL,
                            yaw REAL NOT NULL,
                            pitch REAL NOT NULL,
                            created_at INTEGER NOT NULL,
                            PRIMARY KEY (owner_id, name)
                        )
                        """);
            }
        }
    }

    /** 从数据库加载指定玩家的全部家到内存。 */
    public void load(UUID ownerId) throws SQLException {
        var loaded = new ConcurrentHashMap<String, Home>();
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT name, server, world, x, y, z, yaw, pitch, created_at FROM home WHERE owner_id=?")) {
            stmt.setString(1, ownerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Home home = new Home(ownerId, rs.getString("name"),
                            rs.getString("server"), rs.getString("world"),
                            rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                            rs.getFloat("yaw"), rs.getFloat("pitch"),
                            rs.getLong("created_at"));
                    loaded.put(home.name().toLowerCase(Locale.ROOT), home);
                }
            }
        }
        homes.put(ownerId, loaded);
    }

    /** 保存或更新玩家的家，并异步持久化。 */
    public void save(Home home) {
        homes.computeIfAbsent(home.ownerId(), k -> new ConcurrentHashMap<>())
                .put(home.name().toLowerCase(Locale.ROOT), home);
        executeUpdate(() -> {
            String sql = mysql ? """
                    INSERT INTO home(owner_id, name, server, world, x, y, z, yaw, pitch, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE server=VALUES(server), world=VALUES(world),
                        x=VALUES(x), y=VALUES(y), z=VALUES(z), yaw=VALUES(yaw), pitch=VALUES(pitch)
                    """ : """
                    INSERT INTO home(owner_id, name, server, world, x, y, z, yaw, pitch, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(owner_id, name) DO UPDATE SET server=excluded.server, world=excluded.world,
                        x=excluded.x, y=excluded.y, z=excluded.z, yaw=excluded.yaw, pitch=excluded.pitch
                    """;
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, home.ownerId().toString());
                stmt.setString(2, home.name());
                stmt.setString(3, home.server());
                stmt.setString(4, home.world());
                stmt.setDouble(5, home.x());
                stmt.setDouble(6, home.y());
                stmt.setDouble(7, home.z());
                stmt.setFloat(8, home.yaw());
                stmt.setFloat(9, home.pitch());
                stmt.setLong(10, home.createdAt());
                stmt.executeUpdate();
            }
        });
    }

    /** 删除玩家的指定家，并异步持久化。 */
    public void remove(UUID ownerId, String name) {
        var playerHomes = homes.get(ownerId);
        if (playerHomes != null) {
            playerHomes.remove(name.toLowerCase(Locale.ROOT));
        }
        executeUpdate(() -> {
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
                plugin.getLogger().severe("保存家位置失败: " + e.getMessage());
            }
        });
    }

    /** 表示一个可能抛出 SQL 异常的数据库写操作。 */
    @FunctionalInterface
    private interface SqlOperation {
        void run() throws SQLException;
    }
}
