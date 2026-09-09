package org.katacr.katpa.storage;

import org.katacr.katpa.KaTpaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 持久化玩家地标的历史传送记录与收藏关系。
 *
 * 历史传送：玩家成功传送过的玩家地标（按 玩家+地标 去重）；收藏：玩家手动收藏的地标。
 * 与评分/收入共享同一物理连接，仅新增两张表，不影响既有结构。
 */
public final class PwarpMetaStore {
    private final KaTpaPlugin plugin;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "KaTpa-PwarpMeta-Database");
        thread.setDaemon(true);
        return thread;
    });
    private Connection connection;
    private boolean mysql;

    /** 创建绑定插件实例的玩家地标元数据（历史+收藏）存储。 */
    public PwarpMetaStore(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 使用共享数据库连接初始化历史表与收藏表。 */
    public void initialize(Connection sharedConnection, boolean mysql) throws SQLException {
        this.connection = sharedConnection;
        this.mysql = mysql;
        try (var statement = connection.createStatement()) {
            if (mysql) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS player_warp_history (
                            player_uuid VARCHAR(36) NOT NULL,
                            warp_id VARCHAR(36) NOT NULL,
                            visited_at BIGINT NOT NULL,
                            PRIMARY KEY (player_uuid, warp_id)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS player_warp_favorite (
                            player_uuid VARCHAR(36) NOT NULL,
                            warp_id VARCHAR(36) NOT NULL,
                            favorited_at BIGINT NOT NULL,
                            PRIMARY KEY (player_uuid, warp_id)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
            } else {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS player_warp_history (
                            player_uuid TEXT NOT NULL,
                            warp_id TEXT NOT NULL,
                            visited_at INTEGER NOT NULL,
                            PRIMARY KEY (player_uuid, warp_id)
                        )
                        """);
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS player_warp_favorite (
                            player_uuid TEXT NOT NULL,
                            warp_id TEXT NOT NULL,
                            favorited_at INTEGER NOT NULL,
                            PRIMARY KEY (player_uuid, warp_id)
                        )
                        """);
            }
        }
    }

    /** 记录一次成功传送（去重，仅保留最近访问时间）。 */
    public void recordVisit(UUID playerUuid, UUID warpId) {
        long now = System.currentTimeMillis();
        executeUpdate(() -> {
            String sql = mysql ? """
                    INSERT INTO player_warp_history(player_uuid, warp_id, visited_at)
                    VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE visited_at=VALUES(visited_at)
                    """ : """
                    INSERT INTO player_warp_history(player_uuid, warp_id, visited_at)
                    VALUES (?, ?, ?)
                    ON CONFLICT(player_uuid, warp_id) DO UPDATE SET visited_at=excluded.visited_at
                    """;
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, warpId.toString());
                stmt.setLong(3, now);
                stmt.executeUpdate();
            }
        });
    }

    /** 返回玩家最近传送过的地标 ID（按访问时间倒序）。 */
    public List<UUID> history(UUID playerUuid) {
        List<UUID> result = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT warp_id FROM player_warp_history WHERE player_uuid=? ORDER BY visited_at DESC")) {
            stmt.setString(1, playerUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(UUID.fromString(rs.getString("warp_id")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("读取玩家地标历史失败: " + e.getMessage());
        }
        return result;
    }

    /** 切换某玩家对某地标的收藏状态，返回切换后是否收藏。 */
    public boolean toggleFavorite(UUID playerUuid, UUID warpId) {
        boolean nowFav = !isFavorite(playerUuid, warpId);
        if (nowFav) {
            long now = System.currentTimeMillis();
            executeUpdate(() -> {
                String sql = mysql ? """
                        INSERT INTO player_warp_favorite(player_uuid, warp_id, favorited_at)
                        VALUES (?, ?, ?)
                        ON DUPLICATE KEY UPDATE favorited_at=VALUES(favorited_at)
                        """ : """
                        INSERT INTO player_warp_favorite(player_uuid, warp_id, favorited_at)
                        VALUES (?, ?, ?)
                        ON CONFLICT(player_uuid, warp_id) DO NOTHING
                        """;
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, warpId.toString());
                    stmt.setLong(3, now);
                    stmt.executeUpdate();
                }
            });
        } else {
            executeUpdate(() -> {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "DELETE FROM player_warp_favorite WHERE player_uuid=? AND warp_id=?")) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, warpId.toString());
                    stmt.executeUpdate();
                }
            });
        }
        return nowFav;
    }

    /** 判断某玩家是否收藏了某地标。 */
    public boolean isFavorite(UUID playerUuid, UUID warpId) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT 1 FROM player_warp_favorite WHERE player_uuid=? AND warp_id=?")) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, warpId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("查询收藏状态失败: " + e.getMessage());
            return false;
        }
    }

    /** 返回玩家收藏的地标 ID 列表。 */
    public List<UUID> favorites(UUID playerUuid) {
        List<UUID> result = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT warp_id FROM player_warp_favorite WHERE player_uuid=? ORDER BY favorited_at DESC")) {
            stmt.setString(1, playerUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(UUID.fromString(rs.getString("warp_id")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("读取收藏列表失败: " + e.getMessage());
        }
        return result;
    }

    /** 返回当前所有拥有至少一个地标的玩家 UUID（去重）。 */
    public Set<UUID> ownerIds() {
        Set<UUID> result = new HashSet<>();
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT DISTINCT owner_id FROM player_warp")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(UUID.fromString(rs.getString("owner_id")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("读取玩家地标创建者失败: " + e.getMessage());
        }
        return result;
    }

    /** 返回指定创建者拥有的地标 ID 列表（按名称排序）。 */
    public List<UUID> byOwner(UUID ownerId) {
        List<UUID> result = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT id FROM player_warp WHERE owner_id=? ORDER BY name" + (mysql ? "" : " COLLATE NOCASE"))) {
            stmt.setString(1, ownerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(UUID.fromString(rs.getString("id")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("读取创建者地标失败: " + e.getMessage());
        }
        return result;
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
                plugin.getLogger().severe("保存玩家地标元数据失败: " + e.getMessage());
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
