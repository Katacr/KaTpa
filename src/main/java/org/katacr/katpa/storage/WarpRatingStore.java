package org.katacr.katpa.storage;

import org.bukkit.Bukkit;
import org.katacr.katpa.KaTpaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 持久化玩家地标的评分与离线收入挂账，并维护排行榜缓存。 */
public final class WarpRatingStore {
    /** 数据变更广播主题，供其他子服刷新排行榜缓存。 */
    private static final String SYNC_TOPIC = "warp_rating";
    /** 排行榜缓存条目：地标 ID、平均星级与累计加权得分。 */
    public record LeaderboardEntry(UUID warpId, double stars, int score) {
    }

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
    private JdbcConnectionManager connections;
    private boolean mysql;
    /** 排行榜前 N 名缓存（按累计加权得分降序，含无评分地标）。 */
    private volatile List<LeaderboardEntry> leaderboardCache = List.of();
    /** 排行榜缓存的 ID → 条目索引，供 O(1) 查询。 */
    private volatile Map<UUID, LeaderboardEntry> leaderboardIndex = Map.of();

    /** 创建绑定插件实例的评分存储。 */
    public WarpRatingStore(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 使用共享连接管理器初始化评分表与离线收入挂账表。 */
    public void initialize(JdbcConnectionManager connections, boolean mysql) throws SQLException {
        this.connections = connections;
        this.mysql = mysql;
        connections.executeVoid(connection -> {
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
        });
    }

    /** 记录或更新某玩家对某地标的评分（1-5 星），随后重算排行榜缓存。 */
    public void rate(UUID playerUuid, UUID warpId, int stars) {
        long now = System.currentTimeMillis();
        executeUpdate(connection -> {
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
        refreshLeaderboard();
    }

    /** 读取某玩家对某地标已有的评分，未评分返回 0。 */
    public int findRating(UUID playerUuid, UUID warpId) {
        try {
            return connections.execute(connection -> {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT stars FROM player_warp_rating WHERE player_uuid=? AND warp_id=?")) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, warpId.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        return rs.next() ? rs.getInt("stars") : 0;
                    }
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().severe("读取评分失败: " + e.getMessage());
            return 0;
        }
    }

    /** 计算某地标的平均星级（1-5，未评分返回 0）。 */
    public double averageStars(UUID warpId) {
        try {
            return connections.execute(connection -> {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT AVG(stars) AS avg, COUNT(*) AS cnt FROM player_warp_rating WHERE warp_id=?")) {
                    stmt.setString(1, warpId.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next() && rs.getInt("cnt") > 0) {
                            return rs.getDouble("avg");
                        }
                        return 0D;
                    }
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().severe("计算平均星级失败: " + e.getMessage());
            return 0;
        }
    }

    /** 计算某地标的累计加权得分（按权重公式求和）。 */
    public int totalScore(UUID warpId) {
        try {
            return connections.execute(connection -> {
                int total = 0;
                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT stars FROM player_warp_rating WHERE warp_id=?")) {
                    stmt.setString(1, warpId.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            total += scoreOf(rs.getInt("stars"));
                        }
                    }
                }
                return total;
            });
        } catch (SQLException e) {
            plugin.getLogger().severe("计算得分失败: " + e.getMessage());
            return 0;
        }
    }

    /** 返回评分人数。 */
    public int ratingCount(UUID warpId) {
        try {
            return connections.execute(connection -> {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT COUNT(*) AS cnt FROM player_warp_rating WHERE warp_id=?")) {
                    stmt.setString(1, warpId.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        return rs.next() ? rs.getInt("cnt") : 0;
                    }
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().severe("统计评分人数失败: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 返回排行榜前 limit 名缓存（按累计加权得分降序，含无评分地标）。
     *
     * <p>缓存由创建/删除/评分或管理员重载触发重算，读取方不再逐条查库。
     */
    public List<LeaderboardEntry> leaderboard(int limit) {
        List<LeaderboardEntry> cache = leaderboardCache;
        if (limit <= 0 || limit >= cache.size()) {
            return cache;
        }
        return new ArrayList<>(cache.subList(0, limit));
    }

    /** 返回排行榜缓存中的地标条目，未进入前 N 名返回 null。 */
    public LeaderboardEntry cachedEntry(UUID warpId) {
        return leaderboardIndex.get(warpId);
    }

    /** 返回排行榜缓存容量（默认 30）。 */
    public int leaderboardLimit() {
        return Math.max(1, plugin.getConfig().getInt("modules.pwarp.leaderboard-cache-size", 30));
    }

    /** 异步重算排行榜缓存并广播给其他子服。 */
    public void refreshLeaderboard() {
        refreshLeaderboard(true);
    }

    /** 异步重算排行榜缓存；{@code broadcast} 为 false 时用于响应其他子服的变更通知，避免回环。 */
    public void refreshLeaderboard(boolean broadcast) {
        databaseExecutor.execute(() -> {
            List<LeaderboardEntry> computed;
            try {
                computed = computeLeaderboard(leaderboardLimit());
            } catch (SQLException e) {
                plugin.getLogger().severe("重算排行榜缓存失败: " + e.getMessage());
                return;
            }
            applyLeaderboard(computed);
            if (broadcast && plugin.network() != null) {
                plugin.network().notifyDataChanged(SYNC_TOPIC);
            }
        });
    }

    /** 原子替换排行榜缓存与索引。 */
    private void applyLeaderboard(List<LeaderboardEntry> computed) {
        List<LeaderboardEntry> snapshot = List.copyOf(computed);
        Map<UUID, LeaderboardEntry> index = new HashMap<>();
        for (LeaderboardEntry entry : snapshot) {
            index.put(entry.warpId(), entry);
        }
        this.leaderboardCache = snapshot;
        this.leaderboardIndex = Map.copyOf(index);
    }

    /**
     * 计算排行榜前 limit 名。
     *
     * <p>以全部玩家地标为基准（而非仅有评分的），无评分地标也会出现在排行榜中；
     * 得分相同或无评分时保持 player_warp 表默认顺序（稳定排序）。
     */
    private List<LeaderboardEntry> computeLeaderboard(int limit) throws SQLException {
        return connections.execute(connection -> {
            Map<UUID, double[]> aggregates = new HashMap<>();
            Map<UUID, Integer> scores = new HashMap<>();
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT warp_id, stars FROM player_warp_rating")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        UUID id = UUID.fromString(rs.getString("warp_id"));
                        int stars = rs.getInt("stars");
                        double[] agg = aggregates.computeIfAbsent(id, k -> new double[2]);
                        agg[0] += stars;
                        agg[1] += 1;
                        scores.merge(id, scoreOf(stars), Integer::sum);
                    }
                }
            }
            List<UUID> ordered = new ArrayList<>();
            try (PreparedStatement stmt = connection.prepareStatement("SELECT id FROM player_warp")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        ordered.add(UUID.fromString(rs.getString("id")));
                    }
                }
            }
            ordered.sort(Comparator.comparingInt((UUID id) -> scores.getOrDefault(id, 0)).reversed());
            List<LeaderboardEntry> result = new ArrayList<>();
            for (UUID id : ordered) {
                if (result.size() >= limit) {
                    break;
                }
                double[] agg = aggregates.get(id);
                double avg = agg == null || agg[1] == 0 ? 0D : agg[0] / agg[1];
                result.add(new LeaderboardEntry(id, avg, scores.getOrDefault(id, 0)));
            }
            return result;
        });
    }

    /** 累加某创建者的离线待领取收入（原子累加，避免并发覆盖）。 */
    public void addPendingIncome(UUID ownerUuid, double amount) {
        if (amount <= 0) {
            return;
        }
        executeUpdate(connection -> {
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
        double amount;
        try {
            amount = connections.execute(connection -> {
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT amount FROM player_warp_pending_income WHERE owner_uuid=?")) {
                    select.setString(1, ownerUuid.toString());
                    try (ResultSet rs = select.executeQuery()) {
                        return rs.next() ? rs.getDouble("amount") : 0D;
                    }
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().severe("读取待领取收入失败: " + e.getMessage());
            return 0;
        }
        if (amount > 0) {
            executeUpdate(connection -> {
                try (PreparedStatement del = connection.prepareStatement(
                        "DELETE FROM player_warp_pending_income WHERE owner_uuid=?")) {
                    del.setString(1, ownerUuid.toString());
                    del.executeUpdate();
                }
            });
        }
        return amount;
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
                plugin.getLogger().severe("保存评分失败: " + e.getMessage());
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
