package org.katacr.katpa;

import net.byteflux.libby.BukkitLibraryManager;
import net.byteflux.libby.Library;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.katacr.katpa.command.CancelCommand;
import org.katacr.katpa.command.KaTpaCommand;
import org.katacr.katpa.command.ResponseCommand;
import org.katacr.katpa.command.SettingsCommand;
import org.katacr.katpa.command.TargetCommand;
import org.katacr.katpa.listener.PlayerListener;
import org.katacr.katpa.model.RequestType;
import org.katacr.katpa.network.CrossServerService;
import org.katacr.katpa.service.ParticleService;
import org.katacr.katpa.service.RequestService;
import org.katacr.katpa.service.SoundService;
import org.katacr.katpa.service.TeleportService;
import org.katacr.katpa.storage.SettingsStore;
import org.katacr.katpa.ui.InteractionService;
import org.katacr.katpa.util.MessageService;

import java.io.File;

/** KaTpa 插件入口，负责组装服务、注册指令监听器并管理资源生命周期。 */
public final class KaTpaPlugin extends JavaPlugin {
    private MessageService messages;
    private SettingsStore settings;
    private SoundService sounds;
    private ParticleService particles;
    private TeleportService teleports;
    private RequestService requests;
    private InteractionService interactions;
    private CrossServerService network;

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

    /** 初始化配置、SQLite 数据、业务服务和 Bukkit 注册项。 */
    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new MessageService(this);
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
        teleports = new TeleportService(this);
        requests = new RequestService(this);
        try {
            interactions = new InteractionService(this);
        } catch (RuntimeException | LinkageError exception) {
            getLogger().severe("KaTpa 交互平台初始化失败，插件将被禁用: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        network = new CrossServerService(this);
        network.initialize();
        registerCommands();
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getOnlinePlayers().forEach(settings::rememberPlayer);
        getLogger().info("KaTpa 已启用：" + interactions.platformName()
                + " Dialog、聊天交互、双击潜行和 SQLite 设置已就绪。");
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
        if (settings != null) {
            settings.close();
        }
    }

    /** 返回配置消息渲染服务。 */
    public MessageService messages() {
        return messages;
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

    /** 返回 Dialog 和聊天界面服务。 */
    public InteractionService interactions() {
        return interactions;
    }

    /** 返回 KaProxy 跨服通讯与在线玩家发现服务。 */
    public CrossServerService network() {
        return network;
    }

    /** 注册主命令和六组玩家指令及其补全器。 */
    private void registerCommands() {
        KaTpaCommand mainCommand = new KaTpaCommand(this);
        TargetCommand tpa = new TargetCommand(this, RequestType.TPA);
        TargetCommand tpaHere = new TargetCommand(this, RequestType.TPA_HERE);
        SettingsCommand settingsCommand = new SettingsCommand(this);
        command("katap").setExecutor(mainCommand);
        command("katap").setTabCompleter(mainCommand);
        command("tpa").setExecutor(tpa);
        command("tpa").setTabCompleter(tpa);
        command("tpahere").setExecutor(tpaHere);
        command("tpahere").setTabCompleter(tpaHere);
        command("tpaccept").setExecutor(new ResponseCommand(this, true));
        command("tpdeny").setExecutor(new ResponseCommand(this, false));
        command("tpacancel").setExecutor(new CancelCommand(this));
        command("tpasetting").setExecutor(settingsCommand);
        command("tpasetting").setTabCompleter(settingsCommand);
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
