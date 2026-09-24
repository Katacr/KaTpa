package org.katacr.katpa;

import net.byteflux.libby.BukkitLibraryManager;
import net.byteflux.libby.Library;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.katacr.katpa.command.CancelCommand;
import org.katacr.katpa.command.BackCommand;
import org.katacr.katpa.command.DbackCommand;
import org.katacr.katpa.command.KaTpaCommand;
import org.katacr.katpa.command.ResponseCommand;
import org.katacr.katpa.command.SettingsCommand;
import org.katacr.katpa.command.HomeCommand;
import org.katacr.katpa.command.PlayerWarpCommand;
import org.katacr.katpa.command.PwCommand;
import org.katacr.katpa.command.SetHomeCommand;
import org.katacr.katpa.command.SetWarpCommand;
import org.katacr.katpa.command.TargetCommand;
import org.katacr.katpa.command.WarpCommand;
import org.katacr.katpa.listener.PlayerListener;
import org.katacr.katpa.model.RequestType;
import org.katacr.katpa.network.CrossServerService;
import org.katacr.katpa.placeholder.KaTpaPlaceholderExpansion;
import org.katacr.katpa.service.BackService;
import org.katacr.katpa.service.DbackService;
import org.katacr.katpa.service.HomeService;
import org.katacr.katpa.service.ParticleService;
import org.katacr.katpa.service.PlayerWarpService;
import org.katacr.katpa.service.RequestService;
import org.katacr.katpa.service.SoundService;
import org.katacr.katpa.service.TeleportService;
import org.katacr.katpa.service.WarpService;
import org.katacr.katpa.storage.BackStore;
import org.katacr.katpa.storage.HomeStore;
import org.katacr.katpa.storage.PlayerWarpStore;
import org.katacr.katpa.storage.PwarpMetaStore;
import org.katacr.katpa.storage.SettingsStore;
import org.katacr.katpa.storage.WarpRatingStore;
import org.katacr.katpa.storage.WarpStore;
import org.katacr.katpa.ui.InteractionService;
import org.katacr.katpa.util.ConfigUpdater;
import org.katacr.katpa.text.ExternalItemSpriteManager;
import org.katacr.katpa.text.TextParser;
import org.katacr.katpa.util.MessageService;

import java.io.File;

/** KaTpa 插件入口，负责组装服务、注册指令监听器并管理资源生命周期。 */
public final class KaTpaPlugin extends JavaPlugin {
    private MessageService messages;
    private ExternalItemSpriteManager spriteManager;
    private SettingsStore settings;
    private BackStore backStore;
    private SoundService sounds;
    private ParticleService particles;
    private TeleportService teleports;
    private RequestService requests;
    private InteractionService interactions;
    private CrossServerService network;
    private BackService back;
    private DbackService dback;
    private WarpStore warpStore;
    private WarpService warp;
    private HomeStore homeStore;
    private HomeService home;
    private PlayerWarpStore playerWarpStore;
    private WarpRatingStore warpRatingStore;
    private PwarpMetaStore pwarpMetaStore;
    private PlayerWarpService playerWarp;
    private Economy economy;
    private org.katacr.katpa.util.PointsHook points;

    /** 在插件启用前通过 Libby 下载并挂载 SQLite JDBC 运行时依赖。 */
    @Override
    public void onLoad() {
        saveDefaultConfig();
        reloadConfig();
        File librariesDirectory = new File(getDataFolder().getParentFile().getParentFile(), "libraries");
        if (!librariesDirectory.exists() && !librariesDirectory.mkdirs()) {
            throw new IllegalStateException("无法创建依赖库目录: " + librariesDirectory.getAbsolutePath());
        }

        BukkitLibraryManager libraryManager = new BukkitLibraryManager(this, librariesDirectory.getAbsolutePath());
        libraryManager.addMavenCentral();
        libraryManager.addRepository("https://maven.aliyun.com/repository/public");
        loadAdventureLibraries(libraryManager);
        if (getConfig().getString("storage.type", "sqlite").equalsIgnoreCase("mysql")) {
            Library mariaDb = Library.builder()
                    .groupId("org{}mariadb{}jdbc")
                    .artifactId("mariadb-java-client")
                    .version("3.5.6")
                    .build();
            getLogger().info("正在检查 MariaDB JDBC 运行时依赖……");
            libraryManager.loadLibrary(mariaDb);
        } else {
            Library sqlite = Library.builder()
                    .groupId("org{}xerial")
                    .artifactId("sqlite-jdbc")
                    .version("3.50.3.0")
                    .build();
            getLogger().info("正在检查 SQLite JDBC 运行时依赖……");
            libraryManager.loadLibrary(sqlite);
        }
    }

    /** 通过 Libby 挂载 Adventure 运行库（Spigot/旧核心需要；Paper 已有则由核心提供）。 */
    private void loadAdventureLibraries(BukkitLibraryManager libraryManager) {
        String[][] libraries = {
                {"net{}kyori", "adventure-api", "4.26.1"},
                {"net{}kyori", "adventure-key", "4.26.1"},
                {"net{}kyori", "adventure-text-minimessage", "4.26.1"},
                {"net{}kyori", "adventure-text-serializer-legacy", "4.26.1"},
                {"net{}kyori", "adventure-text-serializer-plain", "4.26.1"},
                {"net{}kyori", "adventure-text-serializer-gson", "4.26.1"},
                {"net{}kyori", "adventure-text-serializer-json", "4.26.1"},
                {"net{}kyori", "adventure-text-serializer-commons", "4.26.1"},
                {"net{}kyori", "examination-api", "1.3.0"},
                {"net{}kyori", "examination-string", "1.3.0"},
                {"net{}kyori", "option", "1.1.0"}
        };
        for (String[] entry : libraries) {
            libraryManager.loadLibrary(Library.builder()
                    .groupId(entry[0])
                    .artifactId(entry[1])
                    .version(entry[2])
                    .build());
        }
    }

    /** 返回物品 Sprite（{@code &item:[...]}）管理器。 */
    public ExternalItemSpriteManager spriteManager() {
        return spriteManager;
    }

    /** 初始化配置、SQLite 数据、业务服务和 Bukkit 注册项。 */
    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (ConfigUpdater.checkAndUpdateConfig(this, new File(getDataFolder(), "config.yml"))) {
            reloadConfig();
        }
        messages = new MessageService(this);
        spriteManager = new ExternalItemSpriteManager(this);
        spriteManager.init();
        TextParser.setSpriteResolver(spriteManager::resolveTag);
        String adventureSource = "未知";
        try {
            java.security.CodeSource source = net.kyori.adventure.text.Component.class.getProtectionDomain().getCodeSource();
            if (source != null && source.getLocation() != null) {
                adventureSource = new File(source.getLocation().toURI()).getName();
            }
        } catch (Throwable ignored) {
        }
        getLogger().info("Adventure 来源=" + adventureSource
                + "，MiniMessage=" + org.katacr.katpa.text.AdventureCompatibility.supportsMiniMessage()
                + "，Sprite(1.21.9+)=" + org.katacr.katpa.text.MinecraftFeatures.supportsSpriteObjects());
        settings = new SettingsStore(this);
        try {
            settings.initialize();
        } catch (Exception exception) {
            getLogger().severe("KaTpa 数据库初始化失败，插件将被禁用: " + exception.getMessage());
            exception.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        sounds = new SoundService(this);
        particles = new ParticleService(this);
        if (moduleEnabled("tpa")) {
            teleports = new TeleportService(this);
            requests = new RequestService(this);
        }
        try {
            interactions = new InteractionService(this);
        } catch (RuntimeException | LinkageError exception) {
            getLogger().severe("KaTpa 交互平台初始化失败，插件将被禁用: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        network = new CrossServerService(this);
        network.initialize();
        if (moduleEnabled("back") || moduleEnabled("dback")) {
            backStore = new BackStore(this);
            try {
                backStore.initialize(settings.connections(), settings.isMysql());
            } catch (Exception e) {
                getLogger().severe("KaTpa 返回位置数据库初始化失败: " + e.getMessage());
            }
        }
        if (moduleEnabled("back")) {
            back = new BackService(this);
        }
        if (moduleEnabled("dback")) {
            dback = new DbackService(this);
        }
        if (moduleEnabled("warp")) {
            warpStore = new WarpStore(this);
            try {
                warpStore.initialize(settings.connections(), settings.isMysql());
            } catch (Exception e) {
                getLogger().severe("KaTpa 地标数据库初始化失败: " + e.getMessage());
            }
            warp = new WarpService(this);
        }
        if (moduleEnabled("home")) {
            homeStore = new HomeStore(this);
            try {
                homeStore.initialize(settings.connections(), settings.isMysql());
            } catch (Exception e) {
                getLogger().severe("KaTpa 家位置数据库初始化失败: " + e.getMessage());
            }
            home = new HomeService(this);
        }
        if (moduleEnabled("pwarp")) {
            playerWarpStore = new PlayerWarpStore(this);
            try {
                playerWarpStore.initialize(settings.connections(), settings.isMysql());
            } catch (Exception e) {
                getLogger().severe("KaTpa 玩家地标数据库初始化失败: " + e.getMessage());
            }
            warpRatingStore = new WarpRatingStore(this);
            try {
                warpRatingStore.initialize(settings.connections(), settings.isMysql());
            } catch (Exception e) {
                getLogger().severe("KaTpa 玩家地标评分数据库初始化失败: " + e.getMessage());
            }
            pwarpMetaStore = new PwarpMetaStore(this);
            try {
                pwarpMetaStore.initialize(settings.connections(), settings.isMysql());
            } catch (Exception e) {
                getLogger().severe("KaTpa 玩家地标元数据初始化失败: " + e.getMessage());
            }
            playerWarp = new PlayerWarpService(this);
        }
        setupEconomy();
        this.points = new org.katacr.katpa.util.PointsHook();
        if (points.available()) {
            getLogger().info("已挂载 PlayerPoints 点券接口。");
        }
        if (warpRatingStore != null) {
            warpRatingStore.refreshLeaderboard(false);
        }
        registerCommands();
        registerPlaceholders();
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getOnlinePlayers().forEach(settings::rememberPlayer);
        var enabledModules = java.util.stream.Stream.of("tpa", "back", "dback", "warp", "home")
                .filter(this::moduleEnabled)
                .toList();
        getLogger().info("KaTpa 已启用：" + interactions.platformName()
                + " 库存菜单、聊天交互、双击潜行和 SQLite 设置已就绪。");
        getLogger().info("已启用模块：" + (enabledModules.isEmpty() ? "无" : String.join(", ", enabledModules)));
    }

    /** 停止所有临时任务并等待数据库写入完成。 */
    @Override
    public void onDisable() {
        if (network != null) {
            network.shutdown();
        }
        if (interactions != null) {
            interactions.shutdown();
        }
        if (requests != null) {
            requests.shutdown();
        }
        if (teleports != null) {
            teleports.shutdown();
        }
        if (backStore != null) {
            backStore.close();
        }
        if (warpStore != null) {
            warpStore.close();
        }
        if (homeStore != null) {
            homeStore.close();
        }
        if (playerWarpStore != null) {
            playerWarpStore.close();
        }
        if (warpRatingStore != null) {
            warpRatingStore.close();
        }
        if (pwarpMetaStore != null) {
            pwarpMetaStore.close();
        }
        if (settings != null) {
            settings.close();
        }
    }

    /** 返回配置消息渲染服务。 */
    public MessageService messages() {
        return messages;
    }

    /**
     * 重载配置、语言与界面资源（gui/ 菜单定义），并重启跨服网络通道。
     *
     * <p>{@code /katap reload} 与 {@code /tpasetting reload} 共用此入口，确保模块配置、
     * 语言文本与菜单编辑全部生效。
     */
    public void reloadAll() {
        if (network != null) {
            network.shutdown();
        }
        reloadConfig();
        messages.reload();
        if (interactions != null) {
            interactions.reload();
        }
        if (network != null) {
            network.initialize();
        }
    }

    /** 返回玩家设置和名单存储。 */
    public SettingsStore settings() {
        return settings;
    }

    /** 返回可配置的交互音效服务。 */
    public SoundService sounds() {
        return sounds;
    }

    /** 返回吟唱粒子效果服务。 */
    public ParticleService particles() {
        return particles;
    }

    /** 返回吟唱与实际传送服务。 */
    public TeleportService teleports() {
        return teleports;
    }

    /** 返回传送请求状态服务。 */
    public RequestService requests() {
        return requests;
    }

    /** 返回库存菜单与聊天交互界面服务。 */
    public InteractionService interactions() {
        return interactions;
    }

    /** 返回 KaProxy 跨服通讯与在线玩家发现服务。 */
    public CrossServerService network() {
        return network;
    }

    /** 返回返回位置持久化存储。 */
    public BackStore backStore() {
        return backStore;
    }

    /** 返回 /back 上次位置服务。 */
    public BackService back() {
        return back;
    }

    /** 返回 /dback 死亡位置服务。 */
    public DbackService dback() {
        return dback;
    }

    /** 返回地标持久化存储。 */
    public WarpStore warpStore() {
        return warpStore;
    }

    /** 返回 /warp 地标传送服务。 */
    public WarpService warp() {
        return warp;
    }

    /** 返回玩家个人家持久化存储。 */
    public HomeStore homeStore() {
        return homeStore;
    }

    /** 返回 /home 家传送服务。 */
    public HomeService home() {
        return home;
    }

    /** 返回玩家地标持久化存储。 */
    public PlayerWarpStore playerWarpStore() {
        return playerWarpStore;
    }

    /** 返回玩家地标评分持久化存储。 */
    public WarpRatingStore warpRatingStore() {
        return warpRatingStore;
    }

    /** 返回玩家地标服务。 */
    public PlayerWarpService playerWarp() {
        return playerWarp;
    }

    /** 返回玩家地标历史与收藏持久化存储。 */
    public PwarpMetaStore pwarpMeta() {
        return pwarpMetaStore;
    }

    /** 返回 Vault 经济接口，未安装时为 null。 */
    public Economy economy() {
        return economy;
    }

    /** 返回 PlayerPoints 点券接口，未安装时仍返回不可用实例（各方法安全降级）。 */
    public org.katacr.katpa.util.PointsHook points() {
        return points;
    }

    /** 返回指定功能模块是否在配置中启用。 */
    public boolean moduleEnabled(String module) {
        return getConfig().getBoolean("modules." + module + ".enabled", true);
    }

    /** 尝试挂载 Vault 经济接口，未安装时静默跳过。 */
    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider<Economy> registration = getServer().getServicesManager().getRegistration(Economy.class);
        if (registration != null) {
            economy = registration.getProvider();
            getLogger().info("已挂载 Vault 经济接口。");
        }
    }

    /** 当 PlaceholderAPI 存在时注册 KaTpa 占位符扩展。 */
    private void registerPlaceholders() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        new KaTpaPlaceholderExpansion(this).register();
        getLogger().info("已注册 PlaceholderAPI 占位符扩展。");
    }

    /** 注册主命令和各功能模块指令及其补全器，已关闭模块注册统一提示。 */
    private void registerCommands() {
        KaTpaCommand mainCommand = new KaTpaCommand(this);
        command("katap").setExecutor(mainCommand);
        command("katap").setTabCompleter(mainCommand);
        if (moduleEnabled("tpa")) {
            TargetCommand tpa = new TargetCommand(this, RequestType.TPA);
            TargetCommand tpaHere = new TargetCommand(this, RequestType.TPA_HERE);
            SettingsCommand settingsCommand = new SettingsCommand(this);
            command("tpa").setExecutor(tpa);
            command("tpa").setTabCompleter(tpa);
            command("tpahere").setExecutor(tpaHere);
            command("tpahere").setTabCompleter(tpaHere);
            command("tpaccept").setExecutor(new ResponseCommand(this, true));
            command("tpdeny").setExecutor(new ResponseCommand(this, false));
            command("tpacancel").setExecutor(new CancelCommand(this));
            command("tpasetting").setExecutor(settingsCommand);
            command("tpasetting").setTabCompleter(settingsCommand);
        } else {
            disabledCommand("tpa", "tpahere", "tpaccept", "tpdeny", "tpacancel", "tpasetting");
        }
        if (moduleEnabled("back")) {
            command("back").setExecutor(new BackCommand(this));
        } else {
            disabledCommand("back");
        }
        if (moduleEnabled("dback")) {
            DbackCommand dbackCommand = new DbackCommand(this);
            command("dback").setExecutor(dbackCommand);
        } else {
            disabledCommand("dback");
        }
        if (moduleEnabled("warp")) {
            WarpCommand warpCommand = new WarpCommand(this);
            command("warp").setExecutor(warpCommand);
            command("warp").setTabCompleter(warpCommand);
            SetWarpCommand setWarpCommand = new SetWarpCommand(this, false);
            command("setwarp").setExecutor(setWarpCommand);
            command("setwarp").setTabCompleter(setWarpCommand);
            SetWarpCommand delWarpCommand = new SetWarpCommand(this, true);
            command("delwarp").setExecutor(delWarpCommand);
            command("delwarp").setTabCompleter(delWarpCommand);
        } else {
            disabledCommand("warp", "setwarp", "delwarp");
        }
        if (moduleEnabled("home")) {
            HomeCommand homeCommand = new HomeCommand(this);
            command("home").setExecutor(homeCommand);
            command("home").setTabCompleter(homeCommand);
            SetHomeCommand setHomeCommand = new SetHomeCommand(this, false);
            command("sethome").setExecutor(setHomeCommand);
            command("sethome").setTabCompleter(setHomeCommand);
            SetHomeCommand delHomeCommand = new SetHomeCommand(this, true);
            command("delhome").setExecutor(delHomeCommand);
            command("delhome").setTabCompleter(delHomeCommand);
        } else {
            disabledCommand("home", "sethome", "delhome");
        }
        if (moduleEnabled("pwarp")) {
            PlayerWarpCommand pwarpCommand = new PlayerWarpCommand(this);
            command("pwarp").setExecutor(pwarpCommand);
            command("pwarp").setTabCompleter(pwarpCommand);
            PwCommand pwCommand = new PwCommand(this);
            command("pw").setExecutor(pwCommand);
            command("pw").setTabCompleter(pwCommand);
        } else {
            disabledCommand("pwarp", "pw");
        }
    }

    /** 为已关闭模块的指令注册统一"功能已关闭"提示执行器。 */
    private void disabledCommand(String... names) {
        for (String name : names) {
            command(name).setExecutor((sender, cmd, label, args) -> {
                messages().send(sender, "module-disabled");
                return true;
            });
        }
    }

    /** 获取 plugin.yml 中必须存在的指令，缺失时立即报告配置错误。 */
    private PluginCommand command(String name) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            throw new IllegalStateException("plugin.yml 缺少指令: " + name);
        }
        return command;
    }
}
