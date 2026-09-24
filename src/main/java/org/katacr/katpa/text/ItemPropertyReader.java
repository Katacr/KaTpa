package org.katacr.katpa.text;

import org.bukkit.inventory.meta.ItemMeta;

/**
 * 读取物品的 {@code item_model} 与 {@code custom_model_data}（反射兼容旧核心）。
 */
public final class ItemPropertyReader {
   private ItemPropertyReader() {
   }

   public static String getItemModel(ItemMeta meta) {
      return BukkitItemMetaCompat.readItemModel(meta);
   }

   public static Integer getCustomModelId(ItemMeta meta) {
      if (meta == null) {
         return null;
      }
      try {
         return meta.hasCustomModelData() ? meta.getCustomModelData() : null;
      } catch (Throwable ignored) {
         return null;
      }
   }
}
