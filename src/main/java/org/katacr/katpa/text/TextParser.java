package org.katacr.katpa.text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * 文本解析：兼容 legacy 颜色码（& / §、&#RRGGBB）与 MiniMessage。
 *
 * <p>移植自 KaMenu 的 TextParser（Kotlin），裁剪掉 KaMMOUpgrade 不需要的部分
 * （sprite 解析、ItemsAdder/Oraxen 字形适配、Adventure 兼容层）。
 *
 * <p>规则：
 * <ul>
 *   <li>{@code &#RRGGBB} → {@code <color:#RRGGBB>}（忽略可选 AA 透明度）。</li>
 *   <li>文本含 MiniMessage 标签 → 用 MiniMessage 解析；其中的 legacy 颜色码会先转成标签。</li>
 *   <li>否则 → 走原有 legacy 解析（等价 {@link ColorUtils#translateColorCodes}）。</li>
 * </ul>
 *
 * <p>未知标签（如 CraftEngine 的 {@code <image:命名空间:ID>}、{@code <shift:像素>}）会原样保留，
 * 由 CraftEngine 在拦截 container/item/... 数据包时替换；因此必须保持 CE
 * {@code network.intercept-packets.container/item/...: true}。
 */
public final class TextParser {
   private TextParser() {
   }

   private static volatile MiniMessage miniMessage;
   private static volatile boolean miniMessageResolved;

   private static final Pattern HEX_PATTERN = Pattern.compile("&#([0-9a-fA-F]{6})([0-9a-fA-F]{2})?");
   private static final Pattern MINI_TAG_PATTERN = Pattern.compile("<[a-z_]+(?:[:][^>]*)?>", Pattern.CASE_INSENSITIVE);
   private static final Pattern LEGACY_CODE_PATTERN = Pattern.compile("[&\u00a7][0-9a-fA-Fk-oK-OrR]");
   private static final Pattern ITEM_SPRITE_PATTERN = Pattern.compile("&item:\\[([^\\]]+)\\]");

   /** 由插件在启动时注入：{@code &item:[x]} → sprite 标签。 */
   private static volatile java.util.function.Function<String, String> spriteResolver;

   /** legacy 颜色/格式码 → MiniMessage 标签（保持插入顺序，避免替换顺序问题）。 */
   private static final Map<String, String> LEGACY_TO_MINI = new LinkedHashMap<>();

   static {
      String[] colors = {
         "0", "black", "1", "dark_blue", "2", "dark_green", "3", "dark_aqua", "4", "dark_red",
         "5", "dark_purple", "6", "gold", "7", "gray", "8", "dark_gray", "9", "blue",
         "a", "green", "b", "aqua", "c", "red", "d", "light_purple", "e", "yellow", "f", "white"
      };
      for (int i = 0; i < colors.length; i += 2) {
         LEGACY_TO_MINI.put(colors[i], colors[i + 1]);
         LEGACY_TO_MINI.put(colors[i].toUpperCase(), colors[i + 1]);
      }
      String[] formats = {
         "k", "obfuscated", "l", "bold", "m", "strikethrough", "n", "underlined", "o", "italic", "r", "reset"
      };
      for (int i = 0; i < formats.length; i += 2) {
         LEGACY_TO_MINI.put(formats[i], formats[i + 1]);
         LEGACY_TO_MINI.put(formats[i].toUpperCase(), formats[i + 1]);
      }
   }

   /**
    * 解析单行文本为 Component。null/空串返回空组件。
    */
   public static Component parse(String text) {
      if (text == null || text.isEmpty()) {
         return Component.empty();
      }

      String converted = convertHex(text);
      converted = convertItemSprite(converted);

      MiniMessage parser = miniMessage();
      if (parser != null && MINI_TAG_PATTERN.matcher(converted).find()) {
         String mini = LEGACY_CODE_PATTERN.matcher(converted).find() ? convertLegacy(converted) : converted;
         try {
            return parser.deserialize(mini);
         } catch (RuntimeException ignored) {
            // 解析失败时退回 legacy
         }
      }

      return LegacyComponentSerializer.legacySection().deserialize(ColorUtils.translateColorCodes(converted));
   }

   private static String convertItemSprite(String text) {
      Matcher matcher = ITEM_SPRITE_PATTERN.matcher(text);
      if (!matcher.find()) {
         return text;
      }

      java.util.function.Function<String, String> resolver = spriteResolver;
      StringBuffer buffer = new StringBuffer();
      do {
         String tag = null;
         if (MinecraftFeatures.supportsSpriteObjects() && resolver != null) {
            tag = resolver.apply(matcher.group(1));
         }
         matcher.appendReplacement(buffer, Matcher.quoteReplacement(tag == null ? "" : tag));
      } while (matcher.find());
      matcher.appendTail(buffer);
      return buffer.toString();
   }

   /** 由插件注入 {@code &item:[...]} 的解析器（见 {@link ExternalItemSpriteManager#resolveTag(String)}）。 */
   public static void setSpriteResolver(java.util.function.Function<String, String> resolver) {
      spriteResolver = resolver;
   }

   /**
    * 逐行解析（保持行数，便于 lore 使用）。
    */
   public static List<Component> parseList(List<String> lines) {
      List<Component> result = new ArrayList<>();
      if (lines == null) {
         return result;
      }
      for (String line : lines) {
         result.add(parse(line));
      }
      return result;
   }

   /** 解析并写入物品展示名：优先使用组件（保留 MiniMessage/字形），旧核心回退 legacy 字符串。 */
   public static void applyName(ItemMeta meta, String text) {
      if (meta == null) {
         return;
      }
      Component component = withoutItalic(parse(text));
      if (!BukkitItemMetaCompat.setDisplayNameComponent(meta, component)) {
         meta.setDisplayName(toLegacy(component));
      }
   }

   /** 解析并写入物品 Lore：优先使用组件，旧核心回退 legacy 字符串。 */
   public static void applyLore(ItemMeta meta, List<String> lines) {
      if (meta == null) {
         return;
      }
      List<Component> components = new ArrayList<>();
      for (Component component : parseList(lines)) {
         components.add(withoutItalic(component));
      }
      if (!BukkitItemMetaCompat.setLoreComponent(meta, components)) {
         List<String> legacy = new ArrayList<>();
         for (Component component : components) {
            legacy.add(toLegacy(component));
         }
         meta.setLore(legacy);
      }
   }

   /**
    * 关闭斜体：原版自定义物品名/Lore 默认斜体，显式设为非斜体以保持外观正常
    * （与 KaMenu 的 {@code withoutItalic} 一致）。
    */
   public static Component withoutItalic(Component component) {
      return component == null ? null
              : component.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,
                      net.kyori.adventure.text.format.TextDecoration.State.FALSE);
   }

   /** 把 Component 序列化为 legacy {@code §} 字符串（供仍需 String 的 API 使用）。 */
   public static String toLegacy(Component component) {
      return LegacyComponentSerializer.legacySection().serialize(component);
   }

   /** 延迟创建 MiniMessage；旧核心（Adventure 不足）返回 null，自动回退 legacy。 */
   private static MiniMessage miniMessage() {
      if (!miniMessageResolved) {
         miniMessage = AdventureCompatibility.createMiniMessage();
         miniMessageResolved = true;
      }
      return miniMessage;
   }

   private static String convertHex(String text) {
      Matcher matcher = HEX_PATTERN.matcher(text);
      StringBuffer buffer = new StringBuffer();

      while (matcher.find()) {
         matcher.appendReplacement(buffer, Matcher.quoteReplacement("<color:#" + matcher.group(1).toUpperCase() + ">"));
      }

      matcher.appendTail(buffer);
      return buffer.toString();
   }

   private static String convertLegacy(String text) {
      String out = text;
      for (Map.Entry<String, String> entry : LEGACY_TO_MINI.entrySet()) {
         out = out.replace("&" + entry.getKey(), "<" + entry.getValue() + ">")
                  .replace("\u00a7" + entry.getKey(), "<" + entry.getValue() + ">");
      }
      return out;
   }
}
