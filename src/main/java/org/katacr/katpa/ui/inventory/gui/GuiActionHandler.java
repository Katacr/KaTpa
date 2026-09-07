package org.katacr.katpa.ui.inventory.gui;

/**
 * 菜单动作处理器，处理 {@code katpa: <namespace> <payload>} 形式的业务动作。
 *
 * 由 {@link GuiManager#registerActionHandler(String, GuiActionHandler)} 注册，命名空间对应
 * 动作前缀（如 {@code katpa: setting mode cycle} 注册命名空间 {@code setting}）。
 */
public interface GuiActionHandler {
    /**
     * 执行动作。
     *
     * @param player    触发动作的玩家
     * @param namespace 命名空间（{@code katpa:} 后第一个词，如 {@code setting}）
     * @param payload   命名空间之后的剩余参数（已做 {key} 与 PAPI 替换）
     * @param session   当前菜单会话（可读取/写入变量与参数）
     */
    void execute(org.bukkit.entity.Player player, String namespace, String payload, MenuSession session);
}
