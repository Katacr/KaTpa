package org.katacr.katpa.text;

import org.bukkit.Material;
import org.katacr.katpa.util.ItemStacks;

/**
 * 原版材质 → Sprite 引用（移植自 KaMenu 的 MaterialUtils sprite 部分）。
 */
public final class MaterialUtils {
   private MaterialUtils() {
   }

   /** 材质的 MiniMessage sprite 标签；材质无效返回 null。 */
   public static String getSpriteTag(String materialName) {
      ItemSpriteReference reference = getSpriteReference(materialName);
      return reference == null ? null : reference.toMiniMessageTag();
   }

   public static ItemSpriteReference getSpriteReference(String materialName) {
      Material material = ItemStacks.matchMaterial(materialName);
      if (material == null) {
         return null;
      }

      String key = material.getKey().getKey().toLowerCase();
      if (material.isBlock()) {
         ItemSpriteReference block = ItemSpriteReference.of("minecraft:blocks", "minecraft:block/" + key);
         return block != null ? block : ItemSpriteReference.of("minecraft:items", "minecraft:item/" + key);
      }
      ItemSpriteReference item = ItemSpriteReference.of("minecraft:items", "minecraft:item/" + key);
      return item != null ? item : ItemSpriteReference.of("minecraft:blocks", "minecraft:block/" + key);
   }
}
