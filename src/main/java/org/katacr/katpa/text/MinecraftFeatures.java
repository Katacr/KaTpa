package org.katacr.katpa.text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;

/**
 * 由服务端版本决定的平台能力判定（移植自 KaMenu 的 MinecraftFeatures）。
 */
public final class MinecraftFeatures {
   private MinecraftFeatures() {
   }

   private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?");
   private static final int SPRITE_MIN_MAJOR = 1;
   private static final int SPRITE_MIN_MINOR = 21;
   private static final int SPRITE_MIN_PATCH = 9;

   private static Boolean spriteObjectsSupported;

   /** 当前服务端是否支持 1.21.9+ 的 Sprite 文本组件。 */
   public static synchronized boolean supportsSpriteObjects() {
      if (spriteObjectsSupported == null) {
         spriteObjectsSupported = supportsSpriteObjects(Bukkit.getBukkitVersion());
      }
      return spriteObjectsSupported;
   }

   /** 供测试/复用的版本判定。 */
   public static boolean supportsSpriteObjects(String bukkitVersion) {
      if (bukkitVersion == null) {
         return false;
      }
      Matcher matcher = VERSION_PATTERN.matcher(bukkitVersion.trim());
      if (!matcher.find()) {
         return false;
      }

      int major = parseInt(matcher.group(1));
      int minor = parseInt(matcher.group(2));
      int patch = parseInt(matcher.group(3));

      if (major != SPRITE_MIN_MAJOR) {
         return major > SPRITE_MIN_MAJOR;
      }
      if (minor != SPRITE_MIN_MINOR) {
         return minor > SPRITE_MIN_MINOR;
      }
      return patch >= SPRITE_MIN_PATCH;
   }

   private static int parseInt(String value) {
      if (value == null) {
         return 0;
      }
      try {
         return Integer.parseInt(value);
      } catch (NumberFormatException ignored) {
         return 0;
      }
   }
}
