package org.katacr.katpa.ui;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.AcceptMode;
import org.katacr.katpa.model.ListType;
import org.katacr.katpa.model.RequestType;
import org.katacr.katpa.model.TeleportRequest;
import org.katacr.katpa.model.Warp;

import java.util.UUID;

/**
 * KaTpa 交互界面的平台中立门面。
 *
 * 运行时先探测 Paper Dialog API，否则探测 Spigot Bungee Dialog API；具体适配器通过反射加载，
 * 从而保证单个插件 JAR 在另一平台缺少对应 API 类时仍可安全启动。全部方法委托给选定的适配器。
 */
public final class InteractionService {
    private final KaTpaPlugin plugin;
    private final InteractionPlatform platform;

    /** 探测并初始化当前服务器可用的 Dialog 适配器。 */
    public InteractionService(KaTpaPlugin plugin) {
        this.plugin = plugin;
        this.platform = createPlatform(plugin);
        this.platform.initialize(plugin);
    }

    /** 返回当前所用交互平台名称，用于启动日志展示。 */
    public String platformName() {
        return platform instanceof PlatformNamed named ? named.platformName() : "Unknown";
    }

    /** 使用当前服务器核心支持的 API 向玩家发送 ActionBar。 */
    public void sendActionBar(Player player, Component message) {
        platform.sendActionBar(player, message);
    }

    /** 向发送者显示请求成功消息，并在尾部附加携带请求上下文的撤销文本。 */
    public void sendCancellableRequestCreated(Player sender, String receiverName, TeleportRequest request,
                                              String messageKey) {
        platform.sendCancellableRequestCreated(sender, receiverName, request, messageKey);
    }

    /** 按接收者偏好显示一条待处理请求。 */
    public void presentRequest(Player receiver, String senderName, TeleportRequest request, AcceptMode mode) {
        platform.presentRequest(receiver, senderName, request, mode);
    }

    /** 打开待处理请求列表，并记录需要在请求变化时接收更新的玩家。 */
    public void showRequestList(Player receiver) {
        platform.showRequestList(receiver);
    }

    /** 请求发生增删后，仅在列表已打开时直接推送最新 Dialog。 */
    public void refreshRequestList(Player receiver) {
        platform.refreshRequestList(receiver);
    }

    /** 停止指定玩家后续接收请求列表变化推送。 */
    public void stopRequestList(UUID playerId) {
        platform.stopRequestList(playerId);
    }

    /** 显示未指定玩家参数时使用的在线玩家选择器。 */
    public void showPlayerSelector(Player player, RequestType type) {
        platform.showPlayerSelector(player, type);
    }

    /** 显示用于选择接受模式和管理名单的个人设置界面。 */
    public void showSettings(Player player) {
        platform.showSettings(player);
    }

    /** 显示某一名单的现有成员、快捷移除入口和添加成员按钮。 */
    public void showRelationEditor(Player player, ListType type) {
        platform.showRelationEditor(player, type);
    }

    /** 显示地标选择 Dialog，列出玩家可用地标。 */
    public void showWarpSelector(Player player) {
        platform.showWarpSelector(player);
    }

    /** 显示管理员地标管理 Dialog。 */
    public void showWarpManager(Player player) {
        platform.showWarpManager(player);
    }

    /** 显示单个地标编辑 Dialog。 */
    public void showWarpEditor(Player player, Warp warp) {
        platform.showWarpEditor(player, warp);
    }

    /** 显示玩家个人家选择 Dialog。 */
    public void showHomeSelector(Player player) {
        platform.showHomeSelector(player);
    }

    /** 显示玩家个人家管理 Dialog。 */
    public void showHomeManager(Player player) {
        platform.showHomeManager(player);
    }

    /** 在插件关闭时释放平台适配器状态。 */
    public void shutdown() {
        platform.shutdown();
    }

    /** 按运行时可用的 Dialog API 反射加载对应平台适配器。 */
    private InteractionPlatform createPlatform(KaTpaPlugin plugin) {
        ClassLoader classLoader = plugin.getClass().getClassLoader();
        String adapterClassName;
        if (classAvailable("io.papermc.paper.dialog.Dialog", classLoader)) {
            adapterClassName = "org.katacr.katpa.ui.paper.PaperInteractionPlatform";
        } else if (classAvailable("net.md_5.bungee.api.dialog.Dialog", classLoader)
                && classAvailable("org.bukkit.event.player.PlayerCustomClickEvent", classLoader)) {
            adapterClassName = "org.katacr.katpa.ui.spigot.SpigotInteractionPlatform";
        } else {
            throw new IllegalStateException("未找到兼容的 Paper 或 Spigot Dialog API（需要 Paper 或 Spigot 1.21.6+）");
        }
        try {
            Class<?> adapterClass = Class.forName(adapterClassName, true, classLoader);
            return (InteractionPlatform) adapterClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("无法实例化交互平台适配器: " + adapterClassName, error);
        } catch (LinkageError error) {
            throw new IllegalStateException("无法链接交互平台适配器: " + adapterClassName, error);
        }
    }

    /** 探测目标类是否可在当前核心解析，缺失时不触发类初始化。 */
    private boolean classAvailable(String name, ClassLoader classLoader) {
        try {
            Class.forName(name, false, classLoader);
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    /** 供适配器可选实现，用于向门面暴露平台名称。 */
    public interface PlatformNamed {
        /** 返回适配器所用平台的展示名称。 */
        String platformName();
    }
}
