package org.katacr.katpa.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 记录玩家请求冷却起点，并计算向上取整的剩余秒数。 */
public final class CooldownTracker {
    private final Map<UUID, Long> startedAt = new HashMap<>();

    /** 记录玩家本次有效请求的冷却起始时间。 */
    public void start(UUID playerId, long now) {
        startedAt.put(playerId, now);
    }

    /** 返回剩余的向上取整秒数，过期后自动清理记录。 */
    public long remainingSeconds(UUID playerId, long now, long durationMillis) {
        long remaining = Math.max(0L, durationMillis) - (now - startedAt.getOrDefault(playerId, 0L));
        if (remaining <= 0L) {
            startedAt.remove(playerId);
            return 0L;
        }
        return (remaining + 999L) / 1000L;
    }
}
