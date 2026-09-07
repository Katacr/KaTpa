package org.katacr.katpa.model;

import java.util.UUID;

/** 保存一个地标（warp）的完整定义，供 /warp 传送和 /setwarp 管理使用。 */
public record Warp(
        UUID id,
        String name,
        String server,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        String permission,
        int cooldownSeconds,
        double cost,
        String description,
        String iconMaterial,
        Integer iconCustomData,
        String iconItemModel,
        long createdAt,
        long updatedAt
) {
    /** 默认图标材质（无自定义图标时使用）。 */
    public static final String DEFAULT_ICON = "ENDER_PEARL";
}
