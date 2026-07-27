package org.katacr.katpa.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证请求冷却计时、秒数取整和过期清理行为。 */
class CooldownTrackerTest {
    /** 确认冷却剩余时间向上取整并在边界时刻归零。 */
    @Test
    void countsDownAndExpiresAtBoundary() {
        CooldownTracker tracker = new CooldownTracker();
        UUID playerId = UUID.randomUUID();
        tracker.start(playerId, 10_000L);

        assertEquals(30L, tracker.remainingSeconds(playerId, 10_001L, 30_000L));
        assertEquals(1L, tracker.remainingSeconds(playerId, 39_999L, 30_000L));
        assertEquals(0L, tracker.remainingSeconds(playerId, 40_000L, 30_000L));
        assertEquals(0L, tracker.remainingSeconds(playerId, 40_001L, 30_000L));
    }

    /** 确认零时长配置不会产生冷却。 */
    @Test
    void zeroDurationDisablesWaiting() {
        CooldownTracker tracker = new CooldownTracker();
        UUID playerId = UUID.randomUUID();
        tracker.start(playerId, 100L);

        assertEquals(0L, tracker.remainingSeconds(playerId, 100L, 0L));
    }
}
