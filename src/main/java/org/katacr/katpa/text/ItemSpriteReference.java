package org.katacr.katpa.text;

import java.util.regex.Pattern;

/**
 * 客户端资源包里的一个二维 Sprite 引用，并生成 MiniMessage 标签。
 *
 * <p>移植自 KaMenu 的 ItemSpriteReference。
 */
public final class ItemSpriteReference {
   private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");

   private final String atlas;
   private final String sprite;

   public ItemSpriteReference(String atlas, String sprite) {
      this.atlas = atlas;
      this.sprite = sprite;
   }

   public String getAtlas() {
      return this.atlas;
   }

   public String getSprite() {
      return this.sprite;
   }

   public String toMiniMessageTag() {
      return "<sprite:'" + this.atlas + "':'" + this.sprite + "'>";
   }

   /** 解析 {@code blocks:namespace:path} / {@code items:namespace:path} 简写。 */
   public static ItemSpriteReference parse(String raw) {
      if (raw == null) {
         return null;
      }
      String[] parts = raw.trim().split(":", 3);
      if (parts.length != 3) {
         return null;
      }

      String atlas = normalizeAtlas(parts[0]);
      if (atlas == null) {
         return null;
      }
      String sprite = normalizeKey(parts[1] + ":" + parts[2]);
      return sprite == null ? null : new ItemSpriteReference(atlas, sprite);
   }

   /** 用完整 atlas / sprite 字段创建。 */
   public static ItemSpriteReference of(String atlas, String sprite) {
      String normalizedAtlas = normalizeAtlas(atlas);
      String normalizedSprite = normalizeKey(sprite);
      return normalizedAtlas == null || normalizedSprite == null ? null : new ItemSpriteReference(normalizedAtlas, normalizedSprite);
   }

   /** 把材质包纹理路径规范化为不含 {@code textures/} 与 {@code .png} 的命名空间键。 */
   public static String normalizeTexture(String raw, String defaultNamespace) {
      if (raw == null) {
         return null;
      }

      String value = raw.trim().replace('\\', '/');
      if (value.isEmpty()) {
         return null;
      }
      if (value.endsWith(".png")) {
         value = value.substring(0, value.length() - 4);
      }
      if (value.startsWith("/")) {
         value = value.substring(1);
      }

      if (value.startsWith("assets/")) {
         String path = value.substring("assets/".length());
         int separator = path.indexOf("/textures/");
         if (separator <= 0) {
            return null;
         }
         value = path.substring(0, separator) + ":" + path.substring(separator + "/textures/".length());
      } else if (value.startsWith("textures/")) {
         value = value.substring("textures/".length());
      }

      if (!value.contains(":")) {
         value = defaultNamespace + ":" + value;
      }
      return normalizeKey(value);
   }

   private static String normalizeAtlas(String raw) {
      if (raw == null) {
         return null;
      }

      String lower = raw.trim().toLowerCase();
      String value;
      if (lower.equals("blocks")) {
         value = "minecraft:blocks";
      } else if (lower.equals("items")) {
         value = "minecraft:items";
      } else {
         value = lower.contains(":") ? lower : "minecraft:" + lower;
      }
      return normalizeKey(value);
   }

   private static String normalizeKey(String raw) {
      if (raw == null) {
         return null;
      }
      String normalized = raw.trim().toLowerCase();
      return KEY_PATTERN.matcher(normalized).matches() ? normalized : null;
   }
}
