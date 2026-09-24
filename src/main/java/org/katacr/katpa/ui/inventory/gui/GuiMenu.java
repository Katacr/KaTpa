package org.katacr.katpa.ui.inventory.gui;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 gui/ 目录的 YAML 解析出的菜单定义。
 *
 * 语法沿用 Ka 系列 Container 风格：根节点 {@code title}/{@code Layout}/{@code button}，
 * 每个按钮含 {@code display} 与 {@code actions}（点击类型 → 动作列表）。
 */
public final class GuiMenu {
    private final String title;
    private final List<String> layout;
    private final Map<String, GuiButton> buttons;
    private final ConfigurationSection root;

    private GuiMenu(String title, List<String> layout, Map<String, GuiButton> buttons, ConfigurationSection root) {
        this.title = title;
        this.layout = layout;
        this.buttons = buttons;
        this.root = root;
    }

    /** 从 Bukkit 配置节解析菜单定义。 */
    public static GuiMenu parse(ConfigurationSection root) {
        String title = root.getString("title", "KaTpa");
        List<String> layout = root.getStringList("Layout");
        if (layout.isEmpty()) {
            layout = root.getStringList("layout");
        }
        Map<String, GuiButton> buttons = new LinkedHashMap<>();
        ConfigurationSection buttonSection = root.getConfigurationSection("button");
        if (buttonSection == null) {
            buttonSection = root.getConfigurationSection("buttons");
        }
        if (buttonSection != null) {
            for (String id : buttonSection.getKeys(false)) {
                ConfigurationSection btn = buttonSection.getConfigurationSection(id);
                if (btn != null) {
                    buttons.put(id, GuiButton.parse(btn));
                }
            }
        }
        return new GuiMenu(title, layout, buttons, root);
    }

    public String title() {
        return title;
    }

    public List<String> layout() {
        return layout;
    }

    public Map<String, GuiButton> buttons() {
        return buttons;
    }

    /** 返回菜单原始配置节（用于读取 {@code update} 等非按钮键）。 */
    public ConfigurationSection root() {
        return root;
    }

    /** 返回某槽位对应的按钮字符（空格或越界返回 null）。 */
    public String charAt(int slot) {
        int row = slot / 9;
        int col = slot % 9;
        if (row < 0 || row >= layout.size()) {
            return null;
        }
        String line = layout.get(row);
        if (col < 0 || col >= line.length()) {
            return null;
        }
        String ch = String.valueOf(line.charAt(col));
        return " ".equals(ch) ? null : ch;
    }

    /** 单个按钮定义。 */
    public static final class GuiButton {
        private final String type;
        private final String permission;
        private final ConfigurationSection display;
        private final ConfigurationSection actions;
        private final ConfigurationSection emptyDisplay;
        private final ConfigurationSection emptyActions;
        private final ConfigurationSection lockDisplay;
        private final ConfigurationSection lockActions;
        private final ConfigurationSection litDisplay;
        private final ConfigurationSection unlitDisplay;

        private GuiButton(String type, String permission, ConfigurationSection display, ConfigurationSection actions,
                          ConfigurationSection emptyDisplay, ConfigurationSection emptyActions,
                          ConfigurationSection lockDisplay, ConfigurationSection lockActions,
                          ConfigurationSection litDisplay, ConfigurationSection unlitDisplay) {
            this.type = type;
            this.permission = permission;
            this.display = display;
            this.actions = actions;
            this.emptyDisplay = emptyDisplay;
            this.emptyActions = emptyActions;
            this.lockDisplay = lockDisplay;
            this.lockActions = lockActions;
            this.litDisplay = litDisplay;
            this.unlitDisplay = unlitDisplay;
        }

        private static GuiButton parse(ConfigurationSection section) {
            return new GuiButton(section.getString("type"), section.getString("permission"),
                    section.getConfigurationSection("display"),
                    section.getConfigurationSection("actions"),
                    section.getConfigurationSection("empty-display"),
                    section.getConfigurationSection("empty-actions"),
                    section.getConfigurationSection("lock-display"),
                    section.getConfigurationSection("lock-actions"),
                    section.getConfigurationSection("lit-display"),
                    section.getConfigurationSection("unlit-display"));
        }

        /** 返回列表类型标识（如 {@code MEMBERS_LIST}），非列表按钮返回 null。 */
        public String type() {
            return type;
        }

        /** 返回按钮可见所需的权限节点，未配置返回 null（对所有人可见）。 */
        public String permission() {
            return permission;
        }

        public ConfigurationSection display() {
            return display;
        }

        public ConfigurationSection actions() {
            return actions;
        }

        /** 返回列表未使用槽位的自定义展示配置（{@code empty-display}），未配置返回 null。 */
        public ConfigurationSection emptyDisplay() {
            return emptyDisplay;
        }

        /** 返回未使用槽位被点击时的动作列表（{@code empty-actions} 下的 all/left/right 等）。 */
        public List<String> emptyActionsFor(String clickType) {
            List<String> result = new ArrayList<>();
            if (emptyActions == null) {
                return result;
            }
            result.addAll(emptyActions.getStringList("all"));
            result.addAll(emptyActions.getStringList(clickType));
            return result;
        }

        /** 返回无权限锁定槽位的展示配置（{@code lock-display}），未配置返回 null。 */
        public ConfigurationSection lockDisplay() {
            return lockDisplay;
        }

        /** 返回选中/点亮状态的展示配置（{@code lit-display}），未配置返回 null。 */
        public ConfigurationSection litDisplay() {
            return litDisplay;
        }

        /** 返回未选中/未点亮状态的展示配置（{@code unlit-display}），未配置返回 null。 */
        public ConfigurationSection unlitDisplay() {
            return unlitDisplay;
        }

        /** 返回无权限锁定槽位被点击时的动作列表（{@code lock-actions} 下的 all/left/right 等）。 */
        public List<String> lockActionsFor(String clickType) {
            List<String> result = new ArrayList<>();
            if (lockActions == null) {
                return result;
            }
            result.addAll(lockActions.getStringList("all"));
            result.addAll(lockActions.getStringList(clickType));
            return result;
        }

        /** 返回指定点击类型的动作列表（支持 left/right/all 等）。 */
        public List<String> actionsFor(String clickType) {
            List<String> result = new ArrayList<>();
            if (actions == null) {
                return result;
            }
            List<String> all = actions.getStringList("all");
            if (all != null) {
                result.addAll(all);
            }
            List<String> specific = actions.getStringList(clickType);
            if (specific != null) {
                result.addAll(specific);
            }
            return result;
        }
    }
}
