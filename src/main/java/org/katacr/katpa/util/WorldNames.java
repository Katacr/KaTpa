package org.katacr.katpa.util;

import org.bukkit.Bukkit;

/**
 * 世界显示名解析：若服务器安装 Multiverse-Core，优先返回其世界别名（alias），
 * 未设置别名时回退世界名；未安装 Multiverse-Core 或解析失败时回退 Bukkit 世界名。
 *
 * <p>Multiverse-Core 作为可选依赖，通过反射调用其 API，避免编译期依赖与运行时 LinkageError：
 * <pre>
 *   MultiverseCoreApi.get().getWorldManager().getWorld(String) → Option&lt;MultiverseWorld&gt;
 *   MultiverseWorld.getAliasOrName()
 * </pre>
 * 反射结果缓存，Multiverse 不可用时仅探测一次。
 */
public final class WorldNames {
   private WorldNames() {
   }

   private static volatile boolean resolved;
   private static volatile boolean multiverseAvailable;
   /** MultiverseCoreApi.get() 静态方法。 */
   private static java.lang.reflect.Method apiGet;
   /** MultiverseCoreApi.getWorldManager() 实例方法。 */
   private static java.lang.reflect.Method getWorldManager;
   /** WorldManager.getWorld(String) 实例方法。 */
   private static java.lang.reflect.Method worldManagerGetWorld;
   /** MultiverseWorld.getAliasOrName() 实例方法。 */
   private static java.lang.reflect.Method getAliasOrName;

   /** 返回世界的显示名（Multiverse 别名优先，否则世界名）；worldName 为 null/空时原样返回。 */
   public static String display(String worldName) {
      if (worldName == null || worldName.isBlank()) {
         return worldName;
      }
      resolve();
      if (!multiverseAvailable) {
         return worldName;
      }
      try {
         Object api = apiGet.invoke(null);
         if (api == null) {
            return worldName;
         }
         Object worldManager = getWorldManager.invoke(api);
         if (worldManager == null) {
            return worldName;
         }
         Object option = worldManagerGetWorld.invoke(worldManager, worldName);
         if (option == null) {
            return worldName;
         }
         // vavr Option：isEmpty()/getOrNull() 兼容
         java.lang.reflect.Method isEmpty = option.getClass().getMethod("isEmpty");
         if (Boolean.TRUE.equals(isEmpty.invoke(option))) {
            return worldName;
         }
         Object world = option.getClass().getMethod("get").invoke(option);
         if (world == null) {
            return worldName;
         }
         Object alias = getAliasOrName.invoke(world);
         if (alias instanceof String text && !text.isBlank()) {
            return text;
         }
         return worldName;
      } catch (Throwable ignored) {
         return worldName;
      }
   }

   /** 返回世界对象（{@link Bukkit#getWorld(String)}）的显示名。 */
   public static String display(org.bukkit.World world) {
      return world == null ? null : display(world.getName());
   }

   /** 解析 Multiverse API 反射句柄；只探测一次。 */
   private static void resolve() {
      if (resolved) {
         return;
      }
      synchronized (WorldNames.class) {
         if (resolved) {
            return;
         }
         try {
            Class<?> apiClass = Class.forName("org.mvplugins.multiverse.core.MultiverseCoreApi");
            Class<?> worldClass = Class.forName("org.mvplugins.multiverse.core.world.MultiverseWorld");
            apiGet = apiClass.getMethod("get");
            getWorldManager = apiClass.getMethod("getWorldManager");
            Class<?> worldManagerClass = Class.forName("org.mvplugins.multiverse.core.world.WorldManager");
            worldManagerGetWorld = worldManagerClass.getMethod("getWorld", String.class);
            getAliasOrName = worldClass.getMethod("getAliasOrName");
            multiverseAvailable = true;
         } catch (Throwable ignored) {
            multiverseAvailable = false;
         }
         resolved = true;
      }
   }
}
