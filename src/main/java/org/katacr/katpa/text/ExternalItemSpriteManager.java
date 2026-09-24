package org.katacr.katpa.text;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.katacr.katpa.util.ExternalItemUtils;

/**
 * 管理 {@code &item:[...]} 的手动 Sprite 覆盖与第三方物品二维纹理缓存。
 *
 * <p>移植自 KaMenu 的 ExternalItemSpriteManager。
 */
public final class ExternalItemSpriteManager {
   private static final String FILE_NAME = "item_sprites.yml";

   private final Plugin plugin;
   private final Map<String, ItemSpriteReference> overrides = new ConcurrentHashMap<>();
   private final Map<String, ItemSpriteReference> resolved = new ConcurrentHashMap<>();
   private final Set<String> unresolved = ConcurrentHashMap.newKeySet();
   private final Listener listener = new Listener() {
   };
   private boolean reloadHooksRegistered;

   public ExternalItemSpriteManager(Plugin plugin) {
      this.plugin = plugin;
   }

   /** 释放默认配置、读取覆盖项，并在版本支持时注册插件数据重载回调。 */
   public void init() {
      File file = new File(this.plugin.getDataFolder(), FILE_NAME);
      if (!file.exists()) {
         this.plugin.saveResource(FILE_NAME, false);
      }
      this.reload();
      if (MinecraftFeatures.supportsSpriteObjects()) {
         this.registerReloadHooks();
      }
   }

   /** 重新读取手动覆盖并清空自动解析缓存；返回覆盖条目数。 */
   public int reload() {
      this.overrides.clear();
      this.clearAutoCache();

      File file = new File(this.plugin.getDataFolder(), FILE_NAME);
      if (!file.isFile()) {
         return 0;
      }

      YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
      ConfigurationSection section = yaml.getConfigurationSection("sprites");
      if (section == null) {
         return 0;
      }

      for (String key : section.getKeys(false)) {
         ItemSpriteReference reference = this.parseReference(section, key);
         if (reference == null) {
            this.plugin.getLogger().warning("Invalid item sprite mapping '" + key + "' in " + FILE_NAME);
            continue;
         }
         this.overrides.put(key.trim().toLowerCase(), reference);
      }
      return this.overrides.size();
   }

   /** 将原版材质、第三方物品 ID 或手动别名转换为 MiniMessage Sprite 标签；失败返回 null。 */
   public String resolveTag(String raw) {
      if (raw == null) {
         return null;
      }

      String key = raw.trim().toLowerCase();
      ItemSpriteReference override = this.overrides.get(key);
      if (override != null) {
         return override.toMiniMessageTag();
      }

      String externalId = ExternalItemUtils.normalizeId(raw);
      if (externalId == null) {
         return MaterialUtils.getSpriteTag(raw);
      }

      ItemSpriteReference cached = this.resolved.get(externalId);
      if (cached != null) {
         return cached.toMiniMessageTag();
      }
      if (this.unresolved.contains(externalId)) {
         return this.fallbackTag(raw);
      }

      ItemSpriteReference reference = ExternalItemUtils.sprite(raw);
      if (reference != null) {
         this.resolved.put(externalId, reference);
         return reference.toMiniMessageTag();
      }

      this.unresolved.add(externalId);
      return this.fallbackTag(raw);
   }

   /** 清空第三方 API 解析缓存；材质包插件重载后自动调用。 */
   public void clearAutoCache() {
      this.resolved.clear();
      this.unresolved.clear();
   }

   public void shutdown() {
      this.overrides.clear();
      this.clearAutoCache();
   }

   private String fallbackTag(String raw) {
      ItemStack item = ExternalItemUtils.create(raw);
      return item == null ? null : MaterialUtils.getSpriteTag(item.getType().name());
   }

   private ItemSpriteReference parseReference(ConfigurationSection section, String key) {
      String shortForm = section.getString(key);
      if (shortForm != null) {
         return ItemSpriteReference.parse(shortForm);
      }

      ConfigurationSection value = section.getConfigurationSection(key);
      if (value == null) {
         return null;
      }
      String atlas = value.getString("atlas");
      String sprite = value.getString("sprite");
      return atlas == null || sprite == null ? null : ItemSpriteReference.of(atlas, sprite);
   }

   private void registerReloadHooks() {
      if (this.reloadHooksRegistered) {
         return;
      }
      this.reloadHooksRegistered = true;
      this.registerOptionalEvent("ItemsAdder", "dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent");
      this.registerOptionalEvent("Oraxen", "io.th0rgal.oraxen.api.events.OraxenItemsLoadedEvent");
      this.registerOptionalEvent("Oraxen", "io.th0rgal.oraxen.api.events.OraxenPackGeneratedEvent");
      this.registerOptionalEvent("CraftEngine", "net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent");
      this.registerOptionalEvent("CraftEngine", "net.momirealms.craftengine.bukkit.api.event.AsyncResourcePackGenerateEvent");
   }

   @SuppressWarnings("unchecked")
   private void registerOptionalEvent(String pluginName, String className) {
      Plugin dependency = this.plugin.getServer().getPluginManager().getPlugin(pluginName);
      if (dependency == null) {
         return;
      }

      Class<? extends Event> eventClass;
      try {
         eventClass = (Class<? extends Event>)dependency.getClass().getClassLoader().loadClass(className);
      } catch (Throwable ignored) {
         return;
      }

      EventExecutor executor = (listener, event) -> this.clearAutoCache();
      this.plugin.getServer().getPluginManager().registerEvent(eventClass, this.listener, EventPriority.MONITOR, executor, this.plugin, true);
   }
}
