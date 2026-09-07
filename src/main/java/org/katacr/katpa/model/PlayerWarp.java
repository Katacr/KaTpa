package org.katacr.katpa.model;

import java.util.UUID;

/** 保存一个玩家创建的公共地标（player warp）的完整定义，供 /pwarp 传送、评分与排行榜使用。 */
public record PlayerWarp(
        UUID id,
        UUID ownerId,
        String ownerName,
        String name,
        String server,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        String description,
        String iconMaterial,
        Integer iconCustomData,
        String iconItemModel,
        double cost,
        int cooldownSeconds,
        long createdAt
) {
    /** 默认图标材质（无自定义图标时使用）。 */
    public static final String DEFAULT_ICON = "ENDER_PEARL";
}
