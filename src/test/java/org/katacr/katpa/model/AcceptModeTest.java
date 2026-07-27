package org.katacr.katpa.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 验证接受模式的指令参数兼容与语言节点映射。 */
class AcceptModeTest {
    /** 确认英文、别名和中文输入均映射到预期模式。 */
    @Test
    void parsesSupportedAliases() {
        assertEquals(AcceptMode.DIALOG, AcceptMode.parse("gui"));
        assertEquals(AcceptMode.CHAT, AcceptMode.parse("聊天"));
        assertEquals(AcceptMode.SNEAK, AcceptMode.parse("SHIFT"));
        assertNull(AcceptMode.parse("unknown"));
    }

    /** 确认每个模式使用稳定的 lang 节点。 */
    @Test
    void exposesLanguageKeys() {
        assertEquals("mode.dialog", AcceptMode.DIALOG.languageKey());
        assertEquals("mode.chat", AcceptMode.CHAT.languageKey());
        assertEquals("mode.sneak", AcceptMode.SNEAK.languageKey());
    }
}
