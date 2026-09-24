package org.katacr.katpa.storage;

import org.bukkit.Bukkit;
import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.PlayerWarp;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
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

/** 持久化玩家创建的公共地标定义，支持 SQLite 单服和 MySQL 跨服共享。 */
public final class PlayerWarpStore {
    /** 数据变更广播主题，供其他子服刷新缓存。 */
    private static final String SYNC_TOPIC = "player_warp";
    private final KaTpaPlugin plugin;
    private final ConcurrentMap<UUID, PlayerWarp> warps = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> nameIndex = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<String, UUID>> ownerIndex = new ConcurrentHashMap<>();
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "KaTpa-PlayerWarp-Database");
        thread.setDaemon(true);
        return thread;
    });
    private JdbcConnectionManager connections;
    private boolean mysql;

    /** 创建绑定插件实例的玩家地标存储。 */
    public PlayerWarpStore(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 使用共享连接管理器初始化表结构并加载全部玩家地标。 */
    public void initialize(JdbcConnectionManager connections, boolean mysql) throws SQLException {
        this.connections = connections;
        this.mysql = mysql;
        connections.executeVoid(connection -> {
            try (var statement = connection.createStatement()) {
                if (mysql) {
                    statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS player_warp (
                            id VARCHAR(36) PRIMARY KEY,
                            owner_id VARCHAR(36) NOT NULL,
                            owner_name VARCHAR(64) NOT NULL DEFAULT '',
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
                            cost DOUBLE NOT NULL DEFAULT 0,
                            created_at BIGINT NOT NULL
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
                } else {
                    statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS player_warp (
                            id TEXT PRIMARY KEY,
                            owner_id TEXT NOT NULL,
                            owner_name TEXT NOT NULL DEFAULT '',
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
                            description TEXT NOT NULL DEFAULT '',
                            icon_material TEXT NOT NULL DEFAULT '',
                            icon_custom_data INTEGER,
                            icon_item_model TEXT NOT NULL DEFAULT '',
                            cost REAL NOT NULL DEFAULT 0,
                            created_at INTEGER NOT NULL
                        )
                        """);
                }
            }
        });
        loadAll();
    }

    /** 从数据库加载全部玩家地标到内存。 */
    public void loadAll() throws SQLException {
        applyLoaded(readAll());
    }

    /** 异步从数据库重载全部玩家地标到内存（用于跨服变更后刷新本地缓存）。 */
    public void reload() {
        databaseExecutor.execute(() -> {
            Loaded loaded;
            try {
                loaded = readAll();
            } catch (SQLException e) {
                plugin.getLogger().warning("刷新玩家地标缓存失败: " + e.getMessage());
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> applyLoaded(loaded));
        });
    }

    /** 读取全表到局部映射，不触碰共享缓存，供主线程安全应用。 */
    private Loaded readAll() throws SQLException {
        return connections.execute(connection -> {
            Map<UUID, PlayerWarp> loadedWarps = new HashMap<>();
            Map<String, UUID> loadedNames = new HashMap<>();
            Map<UUID, ConcurrentMap<String, UUID>> loadedOwners = new HashMap<>();
            try (var statement = connection.createStatement();
                 var rs = statement.executeQuery(
                           "SELECT id, owner_id, owner_name, name, server, server_id, world, world_alias, x, y, z, yaw, pitch, " +
                                   "description, icon_material, icon_custom_data, icon_item_model, cost, " +
                                   "created_at FROM player_warp")) {
                while (rs.next()) {
                    PlayerWarp warp = new PlayerWarp(
                            UUID.fromString(rs.getString("id")),
                            UUID.fromString(rs.getString("owner_id")),
                            rs.getString("owner_name"),
                            rs.getString("name"),
                            rs.getString("server"),
                            rs.getString("server_id"),
                            rs.getString("world"),
                            rs.getString("world_alias"),
                            rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                            rs.getFloat("yaw"), rs.getFloat("pitch"),
                            rs.getString("description"),
                            rs.getString("icon_material"),
                            rs.getObject("icon_custom_data") == null ? null : rs.getInt("icon_custom_data"),
                            rs.getString("icon_item_model"),
                            rs.getDouble("cost"),
                            rs.getLong("created_at"));
                    loadedWarps.put(warp.id(), warp);
                    loadedNames.put(warp.name().toLowerCase(Locale.ROOT), warp.id());
                    loadedOwners.computeIfAbsent(warp.ownerId(), k -> new ConcurrentHashMap<>())
                            .put(warp.name().toLowerCase(Locale.ROOT), warp.id());
                }
            }
            return new Loaded(loadedWarps, loadedNames, loadedOwners);
        });
    }

    /** 用最新快照合并共享缓存：先补入新值再移除已删除项，避免读取方看到空列表。 */
    private void applyLoaded(Loaded loaded) {
        warps.putAll(loaded.warps());
        warps.keySet().retainAll(loaded.warps().keySet());
        nameIndex.putAll(loaded.names());
        nameIndex.keySet().retainAll(loaded.names().keySet());
        ownerIndex.putAll(loaded.owners());
        ownerIndex.keySet().retainAll(loaded.owners().keySet());
    }

    /** 一次全量读取的结果快照。 */
    private record Loaded(Map<UUID, PlayerWarp> warps, Map<String, UUID> names,
                          Map<UUID, ConcurrentMap<String, UUID>> owners) {
    }

    /** 把地标写入内存索引。 */
    private void index(PlayerWarp warp) {
        warps.put(warp.id(), warp);
        nameIndex.put(warp.name().toLowerCase(Locale.ROOT), warp.id());
        ownerIndex.computeIfAbsent(warp.ownerId(), k -> new ConcurrentHashMap<>())
                .put(warp.name().toLowerCase(Locale.ROOT), warp.id());
    }

    /** 创建或更新玩家地标，并异步持久化（不重算排行榜，编辑类写入不影响排行集合）。 */
    public void save(PlayerWarp warp) {
        write(warp, false);
    }

    /** 创建玩家地标并异步持久化，写库成功后重算排行榜缓存。 */
    public void create(PlayerWarp warp) {
        write(warp, true);
    }

    /** 写入玩家地标；{@code refreshLeaderboard} 为 true 时在写库成功后重算排行榜缓存。 */
    private void write(PlayerWarp warp, boolean refreshLeaderboard) {
        index(warp);
        executeUpdate(refreshLeaderboard, connection -> {
            String sql = mysql ? """
                    INSERT INTO player_warp(id, owner_id, owner_name, name, server, server_id, world, world_alias, x, y, z, yaw, pitch,
                        description, icon_material, icon_custom_data, icon_item_model, cost, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE owner_id=VALUES(owner_id), owner_name=VALUES(owner_name), name=VALUES(name),
                        server=VALUES(server), server_id=VALUES(server_id), world=VALUES(world), world_alias=VALUES(world_alias),
                        x=VALUES(x), y=VALUES(y), z=VALUES(z),
                        yaw=VALUES(yaw), pitch=VALUES(pitch), description=VALUES(description), icon_material=VALUES(icon_material),
                        icon_custom_data=VALUES(icon_custom_data), icon_item_model=VALUES(icon_item_model),
                        cost=VALUES(cost)
                    """ : """
                    INSERT INTO player_warp(id, owner_id, owner_name, name, server, server_id, world, world_alias, x, y, z, yaw, pitch,
                        description, icon_material, icon_custom_data, icon_item_model, cost, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET owner_id=excluded.owner_id, owner_name=excluded.owner_name, name=excluded.name,
                        server=excluded.server, server_id=excluded.server_id, world=excluded.world, world_alias=excluded.world_alias,
                        x=excluded.x, y=excluded.y, z=excluded.z,
                        yaw=excluded.yaw, pitch=excluded.pitch, description=excluded.description, icon_material=excluded.icon_material,
                        icon_custom_data=excluded.icon_custom_data, icon_item_model=excluded.icon_item_model,
                        cost=excluded.cost
                    """;
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, warp.id().toString());
                stmt.setString(2, warp.ownerId().toString());
                stmt.setString(3, warp.ownerName() == null ? "" : warp.ownerName());
                stmt.setString(4, warp.name());
                stmt.setString(5, warp.server());
                stmt.setString(6, warp.serverId() == null ? "" : warp.serverId());
                stmt.setString(7, warp.world());
                stmt.setString(8, warp.worldAlias() == null ? "" : warp.worldAlias());
                stmt.setDouble(9, warp.x());
                stmt.setDouble(10, warp.y());
                stmt.setDouble(11, warp.z());
                stmt.setFloat(12, warp.yaw());
                stmt.setFloat(13, warp.pitch());
                stmt.setString(14, warp.description() == null ? "" : warp.description());
                stmt.setString(15, warp.iconMaterial() == null ? "" : warp.iconMaterial());
                if (warp.iconCustomData() == null) {
                    stmt.setNull(16, Types.INTEGER);
                } else {
                    stmt.setInt(16, warp.iconCustomData());
                }
                stmt.setString(17, warp.iconItemModel() == null ? "" : warp.iconItemModel());
                stmt.setDouble(18, warp.cost());
                stmt.setLong(19, warp.createdAt());
                stmt.executeUpdate();
            }
        });
    }

    /** 删除玩家地标，并异步持久化。 */
    public void remove(UUID ownerId, String name) {
        PlayerWarp warp = find(ownerId, name);
        if (warp != null) {
            warps.remove(warp.id());
            nameIndex.remove(name.toLowerCase(Locale.ROOT));
            ConcurrentMap<String, UUID> owned = ownerIndex.get(ownerId);
            if (owned != null) {
                owned.remove(name.toLowerCase(Locale.ROOT));
            }
        }
        executeUpdate(true, connection -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM player_warp WHERE owner_id=? AND name=?")) {
                stmt.setString(1, ownerId.toString());
                stmt.setString(2, name);
                stmt.executeUpdate();
            }
        });
    }

    /** 按拥有者与名称查找玩家地标，大小写不敏感。 */
    public PlayerWarp find(UUID ownerId, String name) {
        ConcurrentMap<String, UUID> owned = ownerIndex.get(ownerId);
        if (owned == null) {
            return null;
        }
        UUID id = owned.get(name.toLowerCase(Locale.ROOT));
        return id == null ? null : warps.get(id);
    }

    /** 按全局名称查找玩家地标，大小写不敏感。 */
    public PlayerWarp find(String name) {
        UUID id = nameIndex.get(name.toLowerCase(Locale.ROOT));
        return id == null ? null : warps.get(id);
    }

    /** 按 ID 查找玩家地标。 */
    public PlayerWarp find(UUID id) {
        return warps.get(id);
    }

    /** 返回指定玩家拥有的全部地标，按名称排序。 */
    public List<PlayerWarp> byOwner(UUID ownerId) {
        ConcurrentMap<String, UUID> owned = ownerIndex.get(ownerId);
        if (owned == null) {
            return new ArrayList<>();
        }
        List<PlayerWarp> result = new ArrayList<>();
        for (UUID id : owned.values()) {
            PlayerWarp warp = warps.get(id);
            if (warp != null) {
                result.add(warp);
            }
        }
        result.sort(Comparator.comparing(PlayerWarp::name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    /** 返回某个玩家拥有的地标数量。 */
    public int count(UUID ownerId) {
        ConcurrentMap<String, UUID> owned = ownerIndex.get(ownerId);
        return owned == null ? 0 : owned.size();
    }

    /** 返回全部玩家地标，按名称排序。 */
    public List<PlayerWarp> all() {
        return warps.values().stream()
                .sorted(Comparator.comparing(PlayerWarp::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** 返回全部玩家地标名称，按名称排序。 */
    public List<String> names() {
        return all().stream().map(PlayerWarp::name).toList();
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

    /** 将数据库写操作放入单线程队列并统一记录异常；写入成功后按需重算排行榜缓存并广播变更。 */
    private void executeUpdate(boolean refreshLeaderboard, SqlOperation operation) {
        databaseExecutor.execute(() -> {
            try {
                connections.executeVoid(operation::run);
            } catch (SQLException e) {
                plugin.getLogger().severe("保存玩家地标失败: " + e.getMessage());
                return;
            }
            // 单线程队列：在本次写入提交后重算排行榜缓存，避免与写事务竞争或漏算。
            if (refreshLeaderboard && plugin.warpRatingStore() != null) {
                plugin.warpRatingStore().refreshLeaderboard();
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
