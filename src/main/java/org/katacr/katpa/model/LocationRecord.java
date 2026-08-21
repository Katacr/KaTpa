package org.katacr.katpa.model;

/** 保存一个跨服可定位的坐标点，用于 /back 和 /dback。 */
public record LocationRecord(
        String server,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        long timestamp
) {
}
