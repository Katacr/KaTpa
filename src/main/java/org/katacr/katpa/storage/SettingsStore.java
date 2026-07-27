package org.katacr.katpa.storage;

import org.bukkit.entity.Player;
import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.AcceptMode;
import org.katacr.katpa.model.KnownPlayer;
import org.katacr.katpa.model.ListType;
import org.katacr.katpa.model.RelationEntry;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 管理玩家接受模式、已知名称与关系名单的 SQLite 持久化缓存。 */
public final class SettingsStore {
    private final KaTpaPlugin plugin;
    private final Map<UUID, AcceptMode> modes = new ConcurrentHashMap<>();
    private final Map<UUID, KnownPlayer> knownPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, RelationEntry>> relations = new ConcurrentHashMap<>();
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "KaTpa-Database");
        thread.setDaemon(true);
        return thread;
    });
    private Connection connection;

    /** 创建绑定插件实例的数据存储。 */
    public SettingsStore(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 初始化数据库结构并一次性载入全部偏好数据。 */
    public void initialize() throws SQLException, ClassNotFoundException {
        plugin.getDataFolder().mkdirs();
        File databaseFile = new File(plugin.getDataFolder(), "players.db");
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=DELETE");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS players (
                        uuid TEXT PRIMARY KEY,
                        last_name TEXT NOT NULL,
                        accept_mode TEXT NOT NULL DEFAULT 'DIALOG',
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS relations (
                        owner_uuid TEXT NOT NULL,
                        target_uuid TEXT NOT NULL,
                        target_name TEXT NOT NULL,
                        list_type TEXT NOT NULL,
                        PRIMARY KEY (owner_uuid, target_uuid)
                    )
                    """);
        }
        loadCache();
    }

    /** 将玩家当前名称写入缓存并异步更新数据库。 */
    public void rememberPlayer(Player player) {
        KnownPlayer knownPlayer = new KnownPlayer(player.getUniqueId(), player.getName());
        knownPlayers.put(player.getUniqueId(), knownPlayer);
        modes.putIfAbsent(player.getUniqueId(), AcceptMode.DIALOG);
        executeUpdate(() -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO players(uuid, last_name, accept_mode, updated_at) VALUES (?, ?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET last_name=excluded.last_name, updated_at=excluded.updated_at
                    """)) {
                statement.setString(1, player.getUniqueId().toString());
                statement.setString(2, player.getName());
                statement.setString(3, mode(player.getUniqueId()).name());
                statement.setLong(4, System.currentTimeMillis());
                statement.executeUpdate();
            }
        });
    }

    /** 返回玩家当前接受模式，未设置时使用 Dialog。 */
    public AcceptMode mode(UUID playerId) {
        return modes.getOrDefault(playerId, AcceptMode.DIALOG);
    }

    /** 更新玩家接受模式并异步持久化。 */
    public void setMode(Player player, AcceptMode mode) {
        rememberPlayer(player);
        modes.put(player.getUniqueId(), mode);
        executeUpdate(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE players SET accept_mode=?, updated_at=? WHERE uuid=?")) {
                statement.setString(1, mode.name());
                statement.setLong(2, System.currentTimeMillis());
                statement.setString(3, player.getUniqueId().toString());
                statement.executeUpdate();
            }
        });
    }

    /** 查询指定玩家是否位于拥有者的某种名单。 */
    public boolean hasRelation(UUID ownerId, UUID targetId, ListType type) {
        RelationEntry entry = relations.getOrDefault(ownerId, Map.of()).get(targetId);
        return entry != null && entry.type() == type;
    }

    /** 添加名单关系，并自动移除目标在另一名单中的记录。 */
    public void setRelation(UUID ownerId, KnownPlayer target, ListType type) {
        relations.computeIfAbsent(ownerId, ignored -> new ConcurrentHashMap<>())
                .put(target.uuid(), new RelationEntry(target.uuid(), target.name(), type));
        executeUpdate(() -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO relations(owner_uuid, target_uuid, target_name, list_type) VALUES (?, ?, ?, ?)
                    ON CONFLICT(owner_uuid, target_uuid) DO UPDATE SET
                        target_name=excluded.target_name, list_type=excluded.list_type
                    """)) {
                statement.setString(1, ownerId.toString());
                statement.setString(2, target.uuid().toString());
                statement.setString(3, target.name());
                statement.setString(4, type.name());
                statement.executeUpdate();
            }
        });
    }

    /** 从拥有者的指定名单中移除目标玩家。 */
    public void removeRelation(UUID ownerId, UUID targetId, ListType type) {
        Map<UUID, RelationEntry> ownerRelations = relations.get(ownerId);
        if (ownerRelations != null) {
            ownerRelations.computeIfPresent(targetId, (ignored, entry) -> entry.type() == type ? null : entry);
        }
        executeUpdate(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM relations WHERE owner_uuid=? AND target_uuid=? AND list_type=?")) {
                statement.setString(1, ownerId.toString());
                statement.setString(2, targetId.toString());
                statement.setString(3, type.name());
                statement.executeUpdate();
            }
        });
    }

    /** 返回拥有者某种名单中的全部玩家，按名称排序。 */
    public List<RelationEntry> relations(UUID ownerId, ListType type) {
        List<RelationEntry> result = new ArrayList<>();
        relations.getOrDefault(ownerId, Map.of()).values().stream()
                .filter(entry -> entry.type() == type)
                .sorted(Comparator.comparing(RelationEntry::targetName, String.CASE_INSENSITIVE_ORDER))
                .forEach(result::add);
        return List.copyOf(result);
    }

    /** 按最后一次名称查找进入过服务器的玩家。 */
    public KnownPlayer findKnownPlayer(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return knownPlayers.values().stream()
                .filter(player -> player.name().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst()
                .orElse(null);
    }

    /** 返回在线玩家对应的已知玩家数据。 */
    public KnownPlayer knownPlayer(Player player) {
        return new KnownPlayer(player.getUniqueId(), player.getName());
    }

    /** 等待异步写入完成并关闭数据库连接。 */
    public void close() {
        databaseExecutor.shutdown();
        try {
            if (!databaseExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("等待数据库任务结束超时。未完成任务将被放弃。");
                databaseExecutor.shutdownNow();
            }
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            databaseExecutor.shutdownNow();
        } catch (SQLException exception) {
            plugin.getLogger().severe("关闭数据库失败: " + exception.getMessage());
        }
    }

    /** 从数据库表中重建内存缓存。 */
    private void loadCache() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT uuid, last_name, accept_mode FROM players")) {
            while (result.next()) {
                UUID playerId = UUID.fromString(result.getString("uuid"));
                String name = result.getString("last_name");
                knownPlayers.put(playerId, new KnownPlayer(playerId, name));
                try {
                    modes.put(playerId, AcceptMode.valueOf(result.getString("accept_mode")));
                } catch (IllegalArgumentException ignored) {
                    modes.put(playerId, AcceptMode.DIALOG);
                }
            }
        }
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT owner_uuid, target_uuid, target_name, list_type FROM relations")) {
            while (result.next()) {
                UUID ownerId = UUID.fromString(result.getString("owner_uuid"));
                UUID targetId = UUID.fromString(result.getString("target_uuid"));
                try {
                    ListType type = ListType.valueOf(result.getString("list_type"));
                    relations.computeIfAbsent(ownerId, ignored -> new ConcurrentHashMap<>())
                            .put(targetId, new RelationEntry(targetId, result.getString("target_name"), type));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("忽略无法识别的名单类型: " + result.getString("list_type"));
                }
            }
        }
    }

    /** 将数据库写操作放入单线程队列并统一记录异常。 */
    private void executeUpdate(SqlOperation operation) {
        databaseExecutor.execute(() -> {
            try {
                operation.run();
            } catch (SQLException exception) {
                plugin.getLogger().severe("保存玩家设置失败: " + exception.getMessage());
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
