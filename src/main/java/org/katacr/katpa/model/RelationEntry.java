package org.katacr.katpa.model;

import java.util.UUID;

/** 保存一条白名单或黑名单关系及目标玩家最后名称。 */
public record RelationEntry(UUID targetId, String targetName, ListType type) {
}
