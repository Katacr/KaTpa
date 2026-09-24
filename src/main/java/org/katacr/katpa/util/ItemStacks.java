package org.katacr.katpa.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 「配置字符串 → 展示用 ItemStack」统一入口。
 *
 * <p>优先解析外部物品（{@code ce:} / {@code craftengine:} / {@code ia:} / {@code itemsadder:} / {@code oraxen:}），
 * 失败则按原版材质解析，最后回退到 {@code fallback}。
 */
public final class ItemStacks {
   private ItemStacks() {
   }

   public static ItemStack resolve(String raw, Player player, Material fallback) {
      Material defaultMaterial = fallback == null ? Material.STONE : fallback;

      if (raw != null && !raw.isBlank()) {
         ItemStack external = ExternalItemUtils.create(raw.trim(), 1, player);
         if (external != null) {
            return external;
         }

         Material material = matchMaterial(raw.trim());
         if (material != null) {
            return new ItemStack(material);
         }
      }

      return new ItemStack(defaultMaterial);
   }

   public static ItemStack resolve(String raw, Material fallback) {
      return resolve(raw, null, fallback);
   }

   /** 原版材质名解析（宽松：大小写、{@code -}/空格、{@code minecraft:} 前缀）。 */
   public static Material matchMaterial(String name) {
      if (name == null || name.isBlank()) {
         return null;
      }

      String normalized = name.trim().toUpperCase().replace('-', '_').replace(' ', '_');
      if (normalized.startsWith("MINECRAFT:")) {
         normalized = normalized.substring("MINECRAFT:".length());
      }

      return Material.matchMaterial(normalized);
   }
}
