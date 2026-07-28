package org.katacr.katpa.model;

import java.util.UUID;

/** 保存 KaProxy 提供的全服在线玩家身份和当前子服名称。 */
public record NetworkPlayer(UUID id, String name, String server) {
}
