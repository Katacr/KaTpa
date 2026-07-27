package org.katacr.katpa.model;

import java.util.UUID;

/** 保存数据库中已知玩家的 UUID 与最后一次名称。 */
public record KnownPlayer(UUID uuid, String name) {
}
