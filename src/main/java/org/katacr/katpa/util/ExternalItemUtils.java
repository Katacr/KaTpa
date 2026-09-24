package org.katacr.katpa.util;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.katacr.katpa.text.CraftEngineResourcePackSpriteResolver;
import org.katacr.katpa.text.ItemPropertyReader;
import org.katacr.katpa.text.ItemSpriteReference;

/**
 * 外部物品解析（配置里写 {@code 前缀:ID}，如 {@code ce:general:pass}、{@code ia:ns:item}、{@code oraxen:item}）。
 *
 * <p>移植自 KaMenu 的 ExternalItemAdapter（去掉 SX-Item），额外提供 {@link #sprite(String)} 用于
 * {@code &item:[...]} 内联图标。所有反射按目标插件 ClassLoader 进行，每次调用都检查插件是否启用。
 */
public final class ExternalItemUtils {
   private ExternalItemUtils() {
   }

   /** 解析出的外部 ID 的规范前缀与值。 */
   public record ParsedId(String prefix, String value) {
   }

   private interface Provider {
      String pluginName();

      String prefix();

      ItemStack create(String id, Player player);

      String idOf(ItemStack item);

      default ItemSpriteReference sprite(String id) {
         return null;
      }
   }

   private static final Provider CRAFT_ENGINE = new CraftEngineProvider();
   private static final Provider ITEMS_ADDER = new ItemsAdderProvider();
   private static final Provider ORAXEN = new OraxenProvider();

   private static final Provider[] PROVIDERS = {ITEMS_ADDER, ORAXEN, CRAFT_ENGINE};

   /** 判断是否为受支持的外部物品写法（前缀合法、值非空）。 */
   public static boolean isExternalId(String raw) {
      return parseId(raw) != null;
   }

   /** 解析配置字符串为 ItemStack；非外部写法 / 插件未装 / 解析失败 → null。 */
   public static ItemStack create(String raw) {
      return create(raw, 1, null);
   }

   public static ItemStack create(String raw, int amount, Player player) {
      ParsedId parsed = parseId(raw);
      if (parsed == null) {
         return null;
      }

      Provider provider = providerOf(parsed.prefix());
      if (provider == null || !enabled(provider.pluginName())) {
         return null;
      }

      ItemStack item = provider.create(parsed.value(), player);
      if (item == null) {
         return null;
      }

      item = item.clone();
      item.setAmount(Math.max(1, amount));
      return item;
   }

   /** 将外部物品 ID 解析为资源包二维 Sprite；无法解析返回 null。 */
   public static ItemSpriteReference sprite(String raw) {
      ParsedId parsed = parseId(raw);
      if (parsed == null) {
         return null;
      }

      Provider provider = providerOf(parsed.prefix());
      if (provider == null || !enabled(provider.pluginName())) {
         return null;
      }
      return provider.sprite(parsed.value());
   }

   /** 判断物品是否匹配配置里的外部 ID。 */
   public static boolean matches(ItemStack item, String raw) {
      ParsedId parsed = parseId(raw);
      if (parsed == null || item == null || item.getType().isAir()) {
         return false;
      }

      Provider provider = providerOf(parsed.prefix());
      if (provider == null || !enabled(provider.pluginName())) {
         return false;
      }

      String id = provider.idOf(item);
      return id != null && id.equalsIgnoreCase(parsed.value());
   }

   /** 识别物品来源，返回 {@code 前缀:原生ID}；无法识别返回 null。 */
   public static String identify(ItemStack item) {
      if (item == null || item.getType().isAir()) {
         return null;
      }

      for (Provider provider : PROVIDERS) {
         if (!enabled(provider.pluginName())) {
            continue;
         }
         String id = provider.idOf(item);
         if (id != null && !id.isEmpty()) {
            return provider.prefix() + ":" + id;
         }
      }

      return null;
   }

   /** 规范化为 {@code 前缀:值小写}，用于缓存键；非法返回 null。 */
   public static String normalizeId(String raw) {
      ParsedId parsed = parseId(raw);
      return parsed == null ? null : parsed.prefix() + ":" + parsed.value().toLowerCase();
   }

   public static ParsedId parseId(String raw) {
      if (raw == null) {
         return null;
      }

      String trimmed = raw.trim();
      int separator = trimmed.indexOf(':');
      if (separator <= 0 || separator == trimmed.length() - 1) {
         return null;
      }

      String prefix = trimmed.substring(0, separator).toLowerCase();
      if (providerOf(prefix) == null) {
         return null;
      }

      String value = trimmed.substring(separator + 1).trim();
      return value.isEmpty() ? null : new ParsedId(prefix, value);
   }

   private static Provider providerOf(String prefix) {
      return switch (prefix) {
         case "craftengine", "ce" -> CRAFT_ENGINE;
         case "itemsadder", "ia" -> ITEMS_ADDER;
         case "oraxen" -> ORAXEN;
         default -> null;
      };
   }

   private static boolean enabled(String pluginName) {
      Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
      return plugin != null && plugin.isEnabled();
   }

   // ===== CraftEngine =====

   private static final class CraftEngineProvider implements Provider {
      @Override
      public String pluginName() {
         return "CraftEngine";
      }

      @Override
      public String prefix() {
         return "craftengine";
      }

      @Override
      public ItemStack create(String id, Player player) {
         return CraftEngineUtils.buildItem(id, player);
      }

      @Override
      public String idOf(ItemStack item) {
         return CraftEngineUtils.getCustomItemId(item);
      }

      @Override
      public ItemSpriteReference sprite(String id) {
         try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("CraftEngine");
            if (plugin == null || !plugin.isEnabled()) {
               return null;
            }

            ItemStack item = CraftEngineUtils.buildItem(id, null);
            if (item == null) {
               return null;
            }

            return CraftEngineResourcePackSpriteResolver.resolve(
               craftEnginePackFile(plugin),
               ItemPropertyReader.getItemModel(item.getItemMeta()),
               ItemPropertyReader.getCustomModelId(item.getItemMeta()),
               id,
               false
            );
         } catch (Throwable ignored) {
            return null;
         }
      }

      /** 读取 CraftEngine 配置中的最终资源包路径。 */
      private static File craftEnginePackFile(Plugin plugin) {
         YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
         String configured = config.getString("resource-pack.path", "./generated/resource_pack.zip");
         if (configured == null || configured.isEmpty()) {
            configured = "./generated/resource_pack.zip";
         }
         File file = new File(configured);
         return file.isAbsolute() ? file : new File(plugin.getDataFolder(), configured);
      }
   }

   // ===== ItemsAdder =====

   private static final class ItemsAdderProvider implements Provider {
      private static final String CUSTOM_STACK = "dev.lone.itemsadder.api.CustomStack";
      private volatile ClassLoader loader;
      private volatile Method getInstance;
      private volatile Method getItemStack;
      private volatile Method byItemStack;
      private volatile Method getNamespacedId;
      private volatile Method getTextures;

      @Override
      public String pluginName() {
         return "ItemsAdder";
      }

      @Override
      public String prefix() {
         return "itemsadder";
      }

      @Override
      public ItemStack create(String id, Player player) {
         try {
            ensure();
            if (getInstance == null || getItemStack == null) {
               return null;
            }
            Object stack = getInstance.invoke(null, id);
            Object item = stack == null ? null : getItemStack.invoke(stack);
            return item instanceof ItemStack ? (ItemStack)item : null;
         } catch (Throwable throwable) {
            return null;
         }
      }

      @Override
      public String idOf(ItemStack item) {
         try {
            ensure();
            if (byItemStack == null || getNamespacedId == null) {
               return null;
            }
            Object stack = byItemStack.invoke(null, item);
            Object id = stack == null ? null : getNamespacedId.invoke(stack);
            return id == null ? null : id.toString();
         } catch (Throwable throwable) {
            return null;
         }
      }

      @Override
      public ItemSpriteReference sprite(String id) {
         try {
            ensure();
            if (getInstance == null || getTextures == null) {
               return null;
            }
            Object stack = getInstance.invoke(null, id);
            Object textures = stack == null ? null : getTextures.invoke(stack);
            if (!(textures instanceof Iterable<?> iterable)) {
               return null;
            }
            for (Object texture : iterable) {
               if (texture == null || texture.toString().isBlank()) {
                  continue;
               }
               String namespace = id.contains(":") ? id.substring(0, id.indexOf(':')) : "minecraft";
               String sprite = ItemSpriteReference.normalizeTexture(texture.toString(), namespace);
               return sprite == null ? null : ItemSpriteReference.of("minecraft:blocks", sprite);
            }
         } catch (Throwable throwable) {
            return null;
         }
         return null;
      }

      private void ensure() {
         Plugin plugin = Bukkit.getPluginManager().getPlugin("ItemsAdder");
         if (plugin == null || !plugin.isEnabled()) {
            return;
         }
         ClassLoader current = plugin.getClass().getClassLoader();
         if (current == loader && getInstance != null) {
            return;
         }
         loader = current;
         try {
            Class<?> clazz = Class.forName(CUSTOM_STACK, true, current);
            getInstance = clazz.getMethod("getInstance", String.class);
            getItemStack = clazz.getMethod("getItemStack");
            byItemStack = clazz.getMethod("byItemStack", ItemStack.class);
            getNamespacedId = clazz.getMethod("getNamespacedID");
            getTextures = clazz.getMethod("getTextures");
         } catch (Throwable throwable) {
            getInstance = null;
            getItemStack = null;
            byItemStack = null;
            getNamespacedId = null;
            getTextures = null;
         }
      }
   }

   // ===== Oraxen =====

   private static final class OraxenProvider implements Provider {
      private static final String ORAXEN_ITEMS = "io.th0rgal.oraxen.api.OraxenItems";
      private volatile ClassLoader loader;
      private volatile Method getItemById;
      private volatile Method idByItem;
      private volatile Method getOraxenMeta;
      private volatile Method getLayers;
      private volatile Method getLayersMap;
      private final Map<Class<?>, Method> buildMethods = new ConcurrentHashMap<>();

      @Override
      public String pluginName() {
         return "Oraxen";
      }

      @Override
      public String prefix() {
         return "oraxen";
      }

      @Override
      public ItemStack create(String id, Player player) {
         try {
            ensure();
            if (getItemById == null) {
               return null;
            }
            Object builder = getItemById.invoke(null, id);
            if (builder == null) {
               return null;
            }
            Method build = buildMethods.computeIfAbsent(builder.getClass(), c -> {
               try {
                  return c.getMethod("build");
               } catch (NoSuchMethodException ignored) {
                  return null;
               }
            });
            Object item = build == null ? null : build.invoke(builder);
            return item instanceof ItemStack ? (ItemStack)item : null;
         } catch (Throwable throwable) {
            return null;
         }
      }

      @Override
      public String idOf(ItemStack item) {
         try {
            ensure();
            if (idByItem == null) {
               return null;
            }
            Object id = idByItem.invoke(null, item);
            return id == null ? null : id.toString();
         } catch (Throwable throwable) {
            return null;
         }
      }

      @Override
      public ItemSpriteReference sprite(String id) {
         try {
            ensure();
            if (getItemById == null || getOraxenMeta == null) {
               return null;
            }
            Object builder = getItemById.invoke(null, id);
            Object meta = builder == null ? null : getOraxenMeta.invoke(builder);
            if (meta == null) {
               return null;
            }

            String texture = null;
            if (getLayers != null) {
               Object layers = getLayers.invoke(meta);
               if (layers instanceof Iterable<?> iterable) {
                  for (Object layer : iterable) {
                     if (layer != null && !layer.toString().isBlank()) {
                        texture = layer.toString();
                        break;
                     }
                  }
               }
            }
            if (texture == null && getLayersMap != null) {
               Object layersMap = getLayersMap.invoke(meta);
               if (layersMap instanceof Map<?, ?> map) {
                  for (Map.Entry<?, ?> entry : map.entrySet()) {
                     if ("layer0".equals(String.valueOf(entry.getKey())) && entry.getValue() != null) {
                        texture = entry.getValue().toString();
                        break;
                     }
                  }
                  if (texture == null) {
                     for (Object value : map.values()) {
                        if (value != null && !value.toString().isBlank()) {
                           texture = value.toString();
                           break;
                        }
                     }
                  }
               }
            }
            if (texture == null) {
               return null;
            }

            String sprite = ItemSpriteReference.normalizeTexture(texture, "minecraft");
            return sprite == null ? null : ItemSpriteReference.of("minecraft:blocks", sprite);
         } catch (Throwable throwable) {
            return null;
         }
      }

      private void ensure() {
         Plugin plugin = Bukkit.getPluginManager().getPlugin("Oraxen");
         if (plugin == null || !plugin.isEnabled()) {
            return;
         }
         ClassLoader current = plugin.getClass().getClassLoader();
         if (current == loader && getItemById != null) {
            return;
         }
         loader = current;
         try {
            Class<?> clazz = Class.forName(ORAXEN_ITEMS, true, current);
            getItemById = clazz.getMethod("getItemById", String.class);
            idByItem = clazz.getMethod("getIdByItem", ItemStack.class);
            Class<?> builderClass = getItemById.getReturnType();
            getOraxenMeta = builderClass.getMethod("getOraxenMeta");
            Class<?> metaClass = getOraxenMeta.getReturnType();
            getLayers = metaClass.getMethod("getLayers");
            getLayersMap = metaClass.getMethod("getLayersMap");
         } catch (Throwable throwable) {
            getItemById = null;
            idByItem = null;
            getOraxenMeta = null;
            getLayers = null;
            getLayersMap = null;
         }
      }
   }
}
