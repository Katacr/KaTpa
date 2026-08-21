package org.katacr.katpa.model;

import java.util.UUID;

/** 保存玩家个人的家位置，供 /home 传送和 /sethome 管理。 */
public record Home(
        UUID ownerId,
        String name,
        String server,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        long createdAt
) {
}
