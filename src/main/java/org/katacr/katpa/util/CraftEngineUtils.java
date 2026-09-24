package org.katacr.katpa.util;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * CraftEngine 物品工具（反射调用 CE API，CraftEngine 非硬依赖）。
 *
 * <p>与旧实现相比的修复：
 * <ul>
 *   <li>使用 CraftEngine 插件的 ClassLoader 加载类（类隔离环境下才可靠）；</li>
 *   <li>不再使用一次性 {@code checked} 缓存——CE 未启用/晚加载时会重试；</li>
 *   <li>缓存 {@code buildBukkitItem} 方法句柄，并支持带玩家上下文构建。</li>
 * </ul>
 */
public final class CraftEngineUtils {
   private static final String API_CLASS = "net.momirealms.craftengine.bukkit.api.CraftEngineItems";

   private static ClassLoader cachedLoader;
   private static Method getCustomItemIdMethod;
   private static Method isCustomItemMethod;
   private static Method byIdMethod;

   private static final Map<Class<?>, Method> BUILD_NO_ARG = new ConcurrentHashMap<>();
   private static final Map<Class<?>, Method> BUILD_WITH_PLAYER = new ConcurrentHashMap<>();

   private CraftEngineUtils() {
   }

   private static boolean ensure() {
      Plugin plugin = Bukkit.getPluginManager().getPlugin("CraftEngine");
      if (plugin == null || !plugin.isEnabled()) {
         invalidate();
         return false;
      }

      ClassLoader loader = plugin.getClass().getClassLoader();
      if (byIdMethod != null && loader == cachedLoader) {
         return true;
      }

      cachedLoader = loader;
      try {
         Class<?> clazz = Class.forName(API_CLASS, true, loader);
         getCustomItemIdMethod = clazz.getMethod("getCustomItemId", ItemStack.class);
         isCustomItemMethod = clazz.getMethod("isCustomItem", ItemStack.class);
         byIdMethod = clazz.getMethod("byId", String.class);
         return true;
      } catch (Throwable throwable) {
         invalidate();
         return false;
      }
   }

   private static void invalidate() {
      cachedLoader = null;
      getCustomItemIdMethod = null;
      isCustomItemMethod = null;
      byIdMethod = null;
   }

   /**
    * @return 物品的 CraftEngine 自定义物品 id（如 {@code minerals_pack:mythril_ingot}）；非 CE 物品或未安装 CE 时返回 null。
    */
   public static String getCustomItemId(ItemStack item) {
      if (item == null || item.getType().isAir()) {
         return null;
      }

      try {
         if (!ensure() || getCustomItemIdMethod == null) {
            return null;
         }

         Object key = getCustomItemIdMethod.invoke(null, item);
         return key == null ? null : key.toString();
      } catch (Throwable throwable) {
         return null;
      }
   }

   public static boolean isCustomItem(ItemStack item) {
      if (item == null || item.getType().isAir()) {
         return false;
      }

      try {
         if (!ensure() || isCustomItemMethod == null) {
            return false;
         }

         Object result = isCustomItemMethod.invoke(null, item);
         return result instanceof Boolean && (Boolean)result;
      } catch (Throwable throwable) {
         return false;
      }
   }

   public static boolean matches(ItemStack item, String id) {
      String customItemId = getCustomItemId(item);
      return customItemId != null && customItemId.equalsIgnoreCase(id);
   }

   /** 构建指定 CE 物品（无玩家上下文）。 */
   public static ItemStack buildItem(String customItemId) {
      return buildItem(customItemId, null);
   }

   /** 构建指定 CE 物品；{@code player} 非空时使用带玩家上下文的 CE API（支持含玩家变量的物品）。 */
   public static ItemStack buildItem(String customItemId, Player player) {
      if (customItemId == null || customItemId.isEmpty()) {
         return null;
      }

      Object definition = definitionOf(customItemId);
      if (definition == null) {
         return null;
      }

      Class<?> clazz = definition.getClass();
      try {
         if (player != null) {
            Method withPlayer = BUILD_WITH_PLAYER.computeIfAbsent(clazz, c -> {
               try {
                  return c.getMethod("buildBukkitItem", Player.class);
               } catch (NoSuchMethodException ignored) {
                  return null;
               }
            });
            if (withPlayer != null) {
               Object item = withPlayer.invoke(definition, player);
               if (item instanceof ItemStack) {
                  return ((ItemStack)item).clone();
               }
            }
         }

         Method noArg = BUILD_NO_ARG.computeIfAbsent(clazz, c -> {
            try {
               return c.getMethod("buildBukkitItem");
            } catch (NoSuchMethodException ignored) {
               return null;
            }
         });
         if (noArg != null) {
            Object item = noArg.invoke(definition);
            if (item instanceof ItemStack) {
               return ((ItemStack)item).clone();
            }
         }
      } catch (Throwable throwable) {
         return null;
      }

      return null;
   }

   private static Object definitionOf(String customItemId) {
      try {
         if (!ensure() || byIdMethod == null) {
            return null;
         }
         return byIdMethod.invoke(null, customItemId);
      } catch (Throwable throwable) {
         return null;
      }
   }
}
