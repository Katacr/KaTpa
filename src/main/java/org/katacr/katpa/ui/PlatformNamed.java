package org.katacr.katpa.ui;

/**
 * 供交互平台适配器可选实现，用于向 {@link InteractionService} 暴露平台展示名称。
 */
public interface PlatformNamed {
    /** 返回适配器所用平台的展示名称。 */
    String platformName();
}
