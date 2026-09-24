package org.katacr.katpa.text;

import java.lang.reflect.Method;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 针对旧/新核心的 ItemMeta / Inventory 组件 API 反射桥。
 *
 * <p>1.16.5 没有 {@code ItemMeta.displayName(Component)} / {@code lore(List)} /
 * {@code Bukkit.createInventory(..., Component)} / {@code ItemMeta.setItemModel}，
 * 因此全部用反射探测，找不到时返回 false/null，由调用方回退 legacy 字符串。
 * 移植自 KaMenu 的 BukkitItemMetaCompat。
 */
public final class BukkitItemMetaCompat {
   private BukkitItemMetaCompat() {
   }

   /** 用 Adventure Component 设置展示名；旧核心返回 false。 */
   public static boolean setDisplayNameComponent(ItemMeta meta, Component component) {
      try {
         Method method = findMethod(ItemMeta.class, meta.getClass(), "displayName", Component.class);
         if (method == null) {
            return false;
         }
         method.setAccessible(true);
         method.invoke(meta, component);
         return true;
      } catch (Throwable ignored) {
         return false;
      }
   }

   /** 用 Adventure Component 列表设置 Lore；旧核心返回 false。 */
   public static boolean setLoreComponent(ItemMeta meta, List<Component> components) {
      try {
         Method method = findMethod(ItemMeta.class, meta.getClass(), "lore", List.class);
         if (method == null) {
            return false;
         }
         method.setAccessible(true);
         method.invoke(meta, components);
         return true;
      } catch (Throwable ignored) {
         return false;
      }
   }

   /** 用 Adventure Component 创建库存；旧核心返回 null。 */
   public static Inventory createInventoryComponent(InventoryHolder holder, int size, Component title) {
      try {
         Method method = findMethod(Bukkit.class, Bukkit.getServer().getClass(), "createInventory", InventoryHolder.class, Integer.TYPE, Component.class);
         if (method == null) {
            return null;
         }
         method.setAccessible(true);
         return (Inventory)method.invoke(Bukkit.getServer(), holder, size, title);
      } catch (Throwable ignored) {
         return null;
      }
   }

   /** 设置物品的 {@code item_model}（1.21.4+）；旧核心返回 false。 */
   public static boolean setItemModel(ItemMeta meta, String itemModel) {
      if (itemModel == null || itemModel.isEmpty()) {
         return false;
      }
      try {
         Class<?> keyClass = Class.forName("org.bukkit.NamespacedKey");
         Method fromString = keyClass.getMethod("fromString", String.class);
         Object key = fromString.invoke(null, itemModel);
         if (key == null) {
            return false;
         }
         Method method = findMethod(ItemMeta.class, meta.getClass(), "setItemModel", keyClass);
         if (method == null) {
            return false;
         }
         method.setAccessible(true);
         method.invoke(meta, key);
         return true;
      } catch (Throwable ignored) {
         return false;
      }
   }

   /** 读取物品的 {@code item_model}；旧核心返回 null。 */
   public static String readItemModel(ItemMeta meta) {
      if (meta == null) {
         return null;
      }
      try {
         Method has = findMethod(ItemMeta.class, meta.getClass(), "hasItemModel");
         Method get = findMethod(ItemMeta.class, meta.getClass(), "getItemModel");
         if (has == null || get == null) {
            return null;
         }
         has.setAccessible(true);
         get.setAccessible(true);
         Object present = has.invoke(meta);
         if (!(present instanceof Boolean) || !((Boolean)present)) {
            return null;
         }
         Object value = get.invoke(meta);
         return value == null ? null : value.toString();
      } catch (Throwable ignored) {
         return null;
      }
   }

   private static Method findMethod(Class<?> primary, Class<?> secondary, String name, Class<?>... parameterTypes) {
      try {
         return primary.getMethod(name, parameterTypes);
      } catch (Throwable ignored) {
      }
      try {
         return secondary.getMethod(name, parameterTypes);
      } catch (Throwable ignored) {
         return null;
      }
   }
}
