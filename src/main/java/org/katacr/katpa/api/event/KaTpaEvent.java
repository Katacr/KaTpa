package org.katacr.katpa.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * KaTpa 传送相关操作事件。
 *
 * 在玩家成功创建家、成功传送到家 / 公共传送点(warp)、成功返回(back)或死亡返回(dback)时触发。
 * 该事件在操作成功后（传送完成、数据已保存）发出，携带操作类型与目标名称。
 *
 * 供其他插件（如 EcoQuests）监听以驱动任务、统计等逻辑。
 * 事件在服务端主线程同步抛出，监听器可直接调用 Bukkit API。
 */
public class KaTpaEvent extends Event {

    /** 操作类型 */
    public enum Action {
        SET_HOME,   // 成功创建/更新家
        HOME,       // 成功传送到家
        WARP,       // 成功传送到公共传送点
        BACK,       // 成功返回上一位置
        DBACK       // 成功返回死亡地点
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Action action;
    private final String name;

    public KaTpaEvent(@NotNull Player player, @NotNull Action action, @Nullable String name) {
        super(false);
        this.player = player;
        this.action = action;
        this.name = name;
    }

    /** 执行操作的玩家 */
    @NotNull
    public Player getPlayer() {
        return player;
    }

    /** 操作类型（SET_HOME / HOME / WARP / BACK / DBACK） */
    @NotNull
    public Action getAction() {
        return action;
    }

    /** 目标名称（家名 / warp 名）；back、dback 为 null */
    @Nullable
    public String getName() {
        return name;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}