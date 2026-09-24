package org.katacr.katpa.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * 检测运行核心提供的 Adventure API 是否足以运行当前 MiniMessage。
 *
 * <p>旧核心（如 Paper 1.16.5 自带的 Adventure 4.7.0）缺少 MiniMessage 依赖的
 * {@code Component.compact()}（4.10.0 才加入），因此必须在首次创建 MiniMessage 前做能力检测。
 * 移植自 KaMenu 的 AdventureCompatibility。
 */
public final class AdventureCompatibility {
   private AdventureCompatibility() {
   }

   private static final String REQUIRED_COMPONENT_METHOD = "compact";

   private static volatile Boolean miniMessageAvailable;

   /** 当前实际加载的 Adventure Component 是否支持新版 MiniMessage。 */
   public static boolean supportsMiniMessage() {
      Boolean cached = miniMessageAvailable;
      if (cached != null) {
         return cached;
      }

      boolean supported;
      try {
         Component.class.getMethod(REQUIRED_COMPONENT_METHOD);
         supported = true;
      } catch (ReflectiveOperationException | LinkageError ignored) {
         supported = false;
      }

      miniMessageAvailable = supported;
      return supported;
   }

   /** 创建 MiniMessage；旧核心或依赖不完整时返回 null。 */
   public static MiniMessage createMiniMessage() {
      if (!supportsMiniMessage()) {
         return null;
      }

      try {
         return MiniMessage.miniMessage();
      } catch (LinkageError | RuntimeException ignored) {
         miniMessageAvailable = false;
         return null;
      }
   }
}
