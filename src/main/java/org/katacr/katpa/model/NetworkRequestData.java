package org.katacr.katpa.model;

import java.util.UUID;

/** 保存代理事件携带的完整跨服传送请求上下文。 */
public record NetworkRequestData(
        UUID id,
        UUID senderId,
        String senderName,
        String senderServer,
        UUID receiverId,
        String receiverName,
        String receiverServer,
        RequestType type,
        long createdAt,
        long expiresAt
) {
    /** 返回实际切换子服的旅行者 UUID。 */
    public UUID travelerId() {
        return type == RequestType.TPA ? senderId : receiverId;
    }

    /** 返回传送目的地玩家 UUID。 */
    public UUID destinationId() {
        return type == RequestType.TPA ? receiverId : senderId;
    }

    /** 返回传送目的地玩家名称。 */
    public String destinationName() {
        return type == RequestType.TPA ? receiverName : senderName;
    }
}
