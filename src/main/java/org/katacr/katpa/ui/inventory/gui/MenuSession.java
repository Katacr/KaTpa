package org.katacr.katpa.ui.inventory.gui;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一次菜单打开的运行时会话，持有菜单 ID、参数与动态变量。
 *
 * 动作处理器可读写 {@link #variables()} 中的值，供同一菜单内按钮间共享状态。
 */
public final class MenuSession {
    private final String menuId;
    private final String args;
    private final Map<String, String> variables;
    private volatile int page;

    /** 创建菜单会话。 */
    public MenuSession(String menuId, String args, Map<String, String> initialVariables) {
        this.menuId = menuId;
        this.args = args == null ? "" : args;
        this.variables = new ConcurrentHashMap<>(initialVariables);
        this.page = 0;
    }

    /** 返回菜单 ID。 */
    public String menuId() {
        return menuId;
    }

    /** 返回打开菜单时传入的参数字符串（如白/黑名单类型）。 */
    public String args() {
        return args;
    }

    /** 返回可读写的变量映射。 */
    public Map<String, String> variables() {
        return variables;
    }

    /** 返回当前页码（从 0 开始）。 */
    public int page() {
        return page;
    }

    /** 设置当前页码。 */
    public void page(int page) {
        this.page = page;
    }

    /** 写入一个变量。 */
    public void set(String key, String value) {
        variables.put(key, value);
    }

    /** 读取一个变量，不存在返回 null。 */
    public String get(String key) {
        return variables.get(key);
    }
}
