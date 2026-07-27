package org.katacr.katpa.ui;

import org.bukkit.entity.Player;
import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.AcceptMode;
import org.katacr.katpa.model.ListType;
import org.katacr.katpa.model.RequestType;
import org.katacr.katpa.model.TeleportRequest;

import java.util.UUID;

/**
 * 隔离 KaTpa 交互界面与具体服务器核心的 Dialog API。
 *
 * 接口只暴露 Bukkit 与 KaTpa 自身类型，避免 Spigot 在加载共享代码时解析 Paper 专属的 Dialog 类；
 * Paper 与 Spigot 各自实现渲染，业务逻辑仍统一留在 RequestService 与 SettingsStore。
 */
public interface InteractionPlatform {
    /** 初始化平台实现所需的监听器与运行时状态。 */
    void initialize(KaTpaPlugin plugin);

    /** 按接收者偏好显示一条待处理请求。 */
    void presentRequest(Player receiver, Player sender, TeleportRequest request, AcceptMode mode);

    /** 打开待处理请求列表，并记录需要在请求变化时接收更新的玩家。 */
    void showRequestList(Player receiver);

    /** 请求发生增删后，仅在列表已打开时直接推送最新 Dialog。 */
    void refreshRequestList(Player receiver);

    /** 停止指定玩家后续接收请求列表变化推送。 */
    void stopRequestList(UUID playerId);

    /** 显示未指定玩家参数时使用的在线玩家选择器。 */
    void showPlayerSelector(Player player, RequestType type);

    /** 显示用于选择接受模式和管理名单的个人设置界面。 */
    void showSettings(Player player);

    /** 显示某一名单的现有成员、快捷移除入口和添加成员按钮。 */
    void showRelationEditor(Player player, ListType type);

    /** 插件关闭时释放平台实现持有的状态。 */
    void shutdown();
}
