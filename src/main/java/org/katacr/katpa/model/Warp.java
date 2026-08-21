package org.katacr.katpa.model;

/** 保存一个地标（warp）的完整定义，供 /warp 传送和 /setwarp 管理使用。 */
public record Warp(
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
        long createdAt,
        long updatedAt
) {
}
