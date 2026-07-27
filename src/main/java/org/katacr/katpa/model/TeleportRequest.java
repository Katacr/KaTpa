package org.katacr.katpa.model;

import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/** 保存一条仍在等待接收方处理的传送请求。 */
public record TeleportRequest(
        UUID id,
        UUID senderId,
        UUID receiverId,
        RequestType type,
        long createdAt,
        long expiresAt,
        BukkitTask timeoutTask
) {
    /** 返回本次传送中实际发生位置变化的玩家。 */
    public UUID travelerId() {
        return type == RequestType.TPA ? senderId : receiverId;
    }

    /** 返回本次传送中作为目的地的玩家。 */
    public UUID destinationId() {
        return type == RequestType.TPA ? receiverId : senderId;
    }
}
