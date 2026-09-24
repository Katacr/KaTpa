package org.katacr.katpa.storage;

import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.LocationRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 持久化玩家上次传送位置和死亡位置，支持 SQLite 与 MySQL 跨服共享。 */
public final class BackStore {
    private final KaTpaPlugin plugin;
    private final ConcurrentMap<UUID, LocationRecord> lastLocations = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, LocationRecord> deathLocations = new ConcurrentHashMap<>();
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "KaTpa-Back-Database");
        thread.setDaemon(true);
        return thread;
    });
    private JdbcConnectionManager connections;
    private boolean mysql;

    /** 创建绑定插件实例的返回位置存储。 */
    public BackStore(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 通过共享连接管理器初始化表结构。 */
    public void initialize(JdbcConnectionManager connections, boolean mysql) throws SQLException {
        this.connections = connections;
        this.mysql = mysql;
        connections.executeVoid(connection -> {
            try (var statement = connection.createStatement()) {
                if (mysql) {
                    statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS last_location (
                            player_id VARCHAR(36) PRIMARY KEY,
                            server VARCHAR(64) NOT NULL,
                            world VARCHAR(128) NOT NULL,
                            x DOUBLE NOT NULL,
                            y DOUBLE NOT NULL,
                            z DOUBLE NOT NULL,
                            yaw FLOAT NOT NULL,
                            pitch FLOAT NOT NULL,
                            timestamp BIGINT NOT NULL
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
                    statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS death_location (
                            player_id VARCHAR(36) NOT NULL,
                            slot INT NOT NULL,
                            server VARCHAR(64) NOT NULL,
                            world VARCHAR(128) NOT NULL,
                            x DOUBLE NOT NULL,
                            y DOUBLE NOT NULL,
                            z DOUBLE NOT NULL,
                            yaw FLOAT NOT NULL,
                            pitch FLOAT NOT NULL,
                            timestamp BIGINT NOT NULL,
                            PRIMARY KEY (player_id, slot)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
                } else {
                    statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS last_location (
                            player_id TEXT PRIMARY KEY,
                            server TEXT NOT NULL,
                            world TEXT NOT NULL,
                            x REAL NOT NULL,
                            y REAL NOT NULL,
                            z REAL NOT NULL,
                            yaw REAL NOT NULL,
                            pitch REAL NOT NULL,
                            timestamp INTEGER NOT NULL
                        )
                        """);
                    statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS death_location (
                            player_id TEXT NOT NULL,
                            slot INTEGER NOT NULL,
                            server TEXT NOT NULL,
                            world TEXT NOT NULL,
                            x REAL NOT NULL,
                            y REAL NOT NULL,
                            z REAL NOT NULL,
                            yaw REAL NOT NULL,
                            pitch REAL NOT NULL,
                            timestamp INTEGER NOT NULL,
                            PRIMARY KEY (player_id, slot)
                        )
                        """);
                }
            }
        });
    }

    /** 异步写入或更新玩家上次位置，并刷新内存缓存。 */
    public void setLastLocation(UUID playerId, LocationRecord location) {
        lastLocations.put(playerId, location);
        executeUpdate(connection -> {
            String sql = mysql ? """
                    INSERT INTO last_location(player_id, server, world, x, y, z, yaw, pitch, timestamp)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE server=VALUES(server), world=VALUES(world),
                        x=VALUES(x), y=VALUES(y), z=VALUES(z), yaw=VALUES(yaw), pitch=VALUES(pitch),
                        timestamp=VALUES(timestamp)
                    """ : """
                    INSERT INTO last_location(player_id, server, world, x, y, z, yaw, pitch, timestamp)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(player_id) DO UPDATE SET server=excluded.server, world=excluded.world,
                        x=excluded.x, y=excluded.y, z=excluded.z, yaw=excluded.yaw, pitch=excluded.pitch,
                        timestamp=excluded.timestamp
                    """;
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, location.server());
                stmt.setString(3, location.world());
                stmt.setDouble(4, location.x());
                stmt.setDouble(5, location.y());
                stmt.setDouble(6, location.z());
                stmt.setFloat(7, location.yaw());
                stmt.setFloat(8, location.pitch());
                stmt.setLong(9, location.timestamp());
                stmt.executeUpdate();
            }
        });
    }

    /** 返回玩家上次位置，内存未命中时返回 null。 */
    public LocationRecord lastLocation(UUID playerId) {
        return lastLocations.get(playerId);
    }

    /** 异步写入死亡位置（只保留最近一次，与 back 一致）并更新内存。 */
    public void setDeathLocation(UUID playerId, LocationRecord location) {
        deathLocations.put(playerId, location);
        executeUpdate(connection -> {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement clear = connection.prepareStatement(
                        "DELETE FROM death_location WHERE player_id=?")) {
                    clear.setString(1, playerId.toString());
                    clear.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO death_location(player_id, slot, server, world, x, y, z, yaw, pitch, timestamp) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    insert.setString(1, playerId.toString());
                    insert.setInt(2, 0);
                    insert.setString(3, location.server());
                    insert.setString(4, location.world());
                    insert.setDouble(5, location.x());
                    insert.setDouble(6, location.y());
                    insert.setDouble(7, location.z());
                    insert.setFloat(8, location.yaw());
                    insert.setFloat(9, location.pitch());
                    insert.setLong(10, location.timestamp());
                    insert.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        });
    }

    /** 返回玩家最近一次死亡位置，无记录时返回 null。 */
    public LocationRecord deathLocation(UUID playerId) {
        return deathLocations.get(playerId);
    }

    /** 玩家进入子服时从共享数据库刷新其位置数据。 */
    public void refresh(UUID playerId) throws SQLException {
        connections.executeVoid(connection -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT server, world, x, y, z, yaw, pitch, timestamp FROM last_location WHERE player_id=?")) {
                stmt.setString(1, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        lastLocations.put(playerId, new LocationRecord(
                                rs.getString("server"), rs.getString("world"),
                                rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                                rs.getFloat("yaw"), rs.getFloat("pitch"), rs.getLong("timestamp")));
                    } else {
                        lastLocations.remove(playerId);
                    }
                }
            }
            List<LocationRecord> loaded = new ArrayList<>();
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT server, world, x, y, z, yaw, pitch, timestamp FROM death_location " +
                            "WHERE player_id=? ORDER BY slot LIMIT 1")) {
                stmt.setString(1, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        loaded.add(new LocationRecord(
                                rs.getString("server"), rs.getString("world"),
                                rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                                rs.getFloat("yaw"), rs.getFloat("pitch"), rs.getLong("timestamp")));
                    }
                }
            }
            if (loaded.isEmpty()) {
                deathLocations.remove(playerId);
            } else {
                deathLocations.put(playerId, loaded.get(0));
            }
        });
    }

    /** 等待异步写入完成（物理连接由 SettingsStore 管理）。 */
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
                plugin.getLogger().severe("保存返回位置失败: " + e.getMessage());
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
