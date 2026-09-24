package org.katacr.katpa.model;

import java.util.UUID;

/** 保存一个地标（warp）的完整定义，供 /warp 传送和 /setwarp 管理使用。 */
public record Warp(
        UUID id,
        String name,
        String server,
        String serverId,
        String world,
        String worldAlias,
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

    /** 返回用于展示的服务器名：优先配置别名 serverId，未设置时回退代理真实服名 server。 */
    public String displayServer() {
        return serverId == null || serverId.isBlank() ? server : serverId;
    }

    /** 返回用于展示的世界名：优先存储的世界别名 worldAlias，未设置时回退世界名 world。 */
    public String displayWorld() {
        return worldAlias == null || worldAlias.isBlank() ? world : worldAlias;
    }
}
