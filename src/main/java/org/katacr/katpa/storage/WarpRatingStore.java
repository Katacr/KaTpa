package org.katacr.katpa.storage;

import org.katacr.katpa.KaTpaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 持久化玩家地标的评分与离线收入挂账。 */
public final class WarpRatingStore {
    /** 评分权重：5★=10 分，4★=5 分，3★=1 分，2★=-5 分，1★=-10 分。 */
    public static int scoreOf(int stars) {
        return switch (stars) {
            case 5 -> 10;
            case 4 -> 5;
            case 3 -> 1;
            case 2 -> -5;
            case 1 -> -10;
            default -> 0;
        };
    }

    private final KaTpaPlugin plugin;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "KaTpa-WarpRating-Database");
        thread.setDaemon(true);
        return thread;
    });
    private Connection connection;
    private boolean mysql;

    /** 创建绑定插件实例的评分存储。 */
    public WarpRatingStore(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 使用共享数据库连接初始化评分表与离线收入挂账表。 */
    public void initialize(Connection sharedConnection, boolean mysql) throws SQLException {
        this.connection = sharedConnection;
        this.mysql = mysql;
        try (var statement = connection.createStatement()) {
            if (mysql) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS player_warp_rating (
                            player_uuid VARCHAR(36) NOT NULL,
                            warp_id VARCHAR(36) NOT NULL,
                            stars TINYINT NOT NULL,
                            rated_at BIGINT NOT NULL,
                            PRIMARY KEY (player_uuid, warp_id)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS player_warp_pending_income (
                            owner_uuid VARCHAR(36) NOT NULL,
                            amount DOUBLE NOT NULL,
                            PRIMARY KEY (owner_uuid)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
            } else {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS player_warp_rating (
                            player_uuid TEXT NOT NULL,
                            warp_id TEXT NOT NULL,
                            stars INTEGER NOT NULL,
                            rated_at INTEGER NOT NULL,
                            PRIMARY KEY (player_uuid, warp_id)
                        )
                        """);
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS player_warp_pending_income (
                            owner_uuid TEXT NOT NULL,
                            amount REAL NOT NULL,
                            PRIMARY KEY (owner_uuid)
                        )
                        """);
            }
        }
    }

    /** 记录或更新某玩家对某地标的评分（1-5 星）。 */
    public void rate(UUID playerUuid, UUID warpId, int stars) {
        long now = System.currentTimeMillis();
        executeUpdate(() -> {
            String sql = mysql ? """
                    INSERT INTO player_warp_rating(player_uuid, warp_id, stars, rated_at)
                    VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE stars=VALUES(stars), rated_at=VALUES(rated_at)
                    """ : """
                    INSERT INTO player_warp_rating(player_uuid, warp_id, stars, rated_at)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT(player_uuid, warp_id) DO UPDATE SET stars=excluded.stars, rated_at=excluded.rated_at
                    """;
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, warpId.toString());
                stmt.setInt(3, stars);
                stmt.setLong(4, now);
                stmt.executeUpdate();
            }
        });
    }

    /** 读取某玩家对某地标已有的评分，未评分返回 0。 */
    public int findRating(UUID playerUuid, UUID warpId) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT stars FROM player_warp_rating WHERE player_uuid=? AND warp_id=?")) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, warpId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt("stars") : 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("读取评分失败: " + e.getMessage());
            return 0;
        }
    }

    /** 计算某地标的平均星级（1-5，未评分返回 0）。 */
    public double averageStars(UUID warpId) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT AVG(stars) AS avg, COUNT(*) AS cnt FROM player_warp_rating WHERE warp_id=?")) {
            stmt.setString(1, warpId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt("cnt") > 0) {
                    return rs.getDouble("avg");
                }
                return 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("计算平均星级失败: " + e.getMessage());
            return 0;
        }
    }

    /** 计算某地标的累计加权得分（按权重公式求和）。 */
    public int totalScore(UUID warpId) {
        int total = 0;
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT stars FROM player_warp_rating WHERE warp_id=?")) {
            stmt.setString(1, warpId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    total += scoreOf(rs.getInt("stars"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("计算得分失败: " + e.getMessage());
        }
        return total;
    }

    /** 返回评分人数。 */
    public int ratingCount(UUID warpId) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT COUNT(*) AS cnt FROM player_warp_rating WHERE warp_id=?")) {
            stmt.setString(1, warpId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt("cnt") : 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("统计评分人数失败: " + e.getMessage());
            return 0;
        }
    }

    /** 返回按累计加权得分降序的前 limit 个地标 ID。 */
    public List<UUID> leaderboard(int limit) {
        List<UUID> result = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT warp_id, SUM(stars) AS weighted FROM player_warp_rating GROUP BY warp_id " +
                        "ORDER BY weighted DESC LIMIT ?")) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(UUID.fromString(rs.getString("warp_id")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("读取排行榜失败: " + e.getMessage());
        }
        return result;
    }

    /** 累加某创建者的离线待领取收入（原子累加，避免并发覆盖）。 */
    public void addPendingIncome(UUID ownerUuid, double amount) {
        if (amount <= 0) {
            return;
        }
        executeUpdate(() -> {
            String sql = mysql ? """
                    INSERT INTO player_warp_pending_income(owner_uuid, amount)
                    VALUES (?, ?)
                    ON DUPLICATE KEY UPDATE amount = amount + VALUES(amount)
                    """ : """
                    INSERT INTO player_warp_pending_income(owner_uuid, amount)
                    VALUES (?, ?)
                    ON CONFLICT(owner_uuid) DO UPDATE SET amount = amount + excluded.amount
                    """;
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, ownerUuid.toString());
                stmt.setDouble(2, amount);
                stmt.executeUpdate();
            }
        });
    }

    /** 读取并清空某创建者的离线待领取收入，无挂账返回 0。 */
    public double takePendingIncome(UUID ownerUuid) {
        double amount = 0;
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT amount FROM player_warp_pending_income WHERE owner_uuid=?")) {
            select.setString(1, ownerUuid.toString());
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    amount = rs.getDouble("amount");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("读取待领取收入失败: " + e.getMessage());
            return 0;
        }
        if (amount > 0) {
            executeUpdate(() -> {
                try (PreparedStatement del = connection.prepareStatement(
                        "DELETE FROM player_warp_pending_income WHERE owner_uuid=?")) {
                    del.setString(1, ownerUuid.toString());
                    del.executeUpdate();
                }
            });
        }
        return amount;
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
                plugin.getLogger().severe("保存评分失败: " + e.getMessage());
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
