package org.katacr.katpa.model;

import java.util.UUID;

/** 保存玩家个人的家位置，供 /home 传送和 /sethome 管理。 */
public record Home(
        UUID id,
        UUID ownerId,
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
        String description,
        String iconMaterial,
        Integer iconCustomData,
        String iconItemModel,
        long createdAt
) {
    /** 默认图标材质（无自定义图标时使用）。 */
    public static final String DEFAULT_ICON = "RED_BED";

    /** 返回用于展示的服务器名：优先配置别名 serverId，未设置时回退代理真实服名 server。 */
    public String displayServer() {
        return serverId == null || serverId.isBlank() ? server : serverId;
    }

    /** 返回用于展示的世界名：优先存储的世界别名 worldAlias，未设置时回退世界名 world。 */
    public String displayWorld() {
        return worldAlias == null || worldAlias.isBlank() ? world : worldAlias;
    }
}
