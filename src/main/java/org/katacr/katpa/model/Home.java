package org.katacr.katpa.model;

import java.util.UUID;

/** 保存玩家个人的家位置，供 /home 传送和 /sethome 管理。 */
public record Home(
        UUID id,
        UUID ownerId,
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
        long createdAt
) {
    /** 默认图标材质（无自定义图标时使用）。 */
    public static final String DEFAULT_ICON = "RED_BED";
}
