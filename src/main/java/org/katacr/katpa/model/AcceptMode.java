package org.katacr.katpa.model;

/** 定义接收玩家处理传送请求时使用的交互方式。 */
public enum AcceptMode {
    DIALOG,
    CHAT,
    SNEAK;

    /** 返回该模式在语言文件中使用的节点名。 */
    public String languageKey() {
        return "mode." + name().toLowerCase();
    }

    /** 将指令参数转换为接受模式，无法识别时返回 null。 */
    public static AcceptMode parse(String value) {
        return switch (value.toLowerCase()) {
            case "dialog", "gui", "对话框" -> DIALOG;
            case "chat", "聊天" -> CHAT;
            case "sneak", "shift", "潜行" -> SNEAK;
            default -> null;
        };
    }
}
