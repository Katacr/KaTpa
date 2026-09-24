package org.katacr.katpa.text;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 从 CraftEngine 最终资源包的物品定义与模型中解析二维纹理。
 *
 * <p>只读标准资源包 JSON，不依赖 CraftEngine 内部注册表。移植自 KaMenu 的
 * CraftEngineResourcePackSpriteResolver。
 */
public final class CraftEngineResourcePackSpriteResolver {
   private CraftEngineResourcePackSpriteResolver() {
   }

   private static final int MAX_MODEL_DEPTH = 16;
   private static final Pattern ATLAS_PATH = Pattern.compile("^assets/([a-z0-9_.-]+)/atlases/([a-z0-9_./-]+)\\.json$");
   private static final Pattern KEY_PART = Pattern.compile("^[a-z0-9_.-]+$");
   private static final Pattern PATH_PART = Pattern.compile("^[a-z0-9_./-]+$");

   /** 根据物品模型、CustomModelData 与物品 ID 查找二维 Sprite；失败返回 null。 */
   public static ItemSpriteReference resolve(File resourcePack, String itemModel, Integer customModelData, String itemId, boolean preferBlocksAtlas) {
      if (resourcePack == null || !resourcePack.isFile()) {
         return null;
      }

      try (ZipFile zip = new ZipFile(resourcePack)) {
         Set<ResourceKey> candidates = new LinkedHashSet<>();
         ResourceKey modelKey = ResourceKey.parse(itemModel, "minecraft");
         if (modelKey != null) {
            candidates.add(modelKey);
         }
         ResourceKey idKey = ResourceKey.parse(itemId, "minecraft");
         if (idKey != null) {
            candidates.add(idKey);
         }

         for (ResourceKey candidate : candidates) {
            ResourceKey model = resolveItemDefinition(zip, candidate, customModelData);
            if (model == null) {
               continue;
            }
            ResourceKey texture = resolveModelTexture(zip, model);
            if (texture == null) {
               continue;
            }
            return resolveAtlas(zip, texture, preferBlocksAtlas);
         }
      } catch (Throwable ignored) {
         // 解析失败按未找到处理
      }

      return null;
   }

   private static ResourceKey resolveItemDefinition(ZipFile zip, ResourceKey key, Integer customModelData) {
      JsonObject json = readJson(zip, "assets/" + key.namespace + "/items/" + key.path + ".json");
      if (json == null) {
         return null;
      }
      JsonElement root = json.get("model");
      return root == null ? null : selectModel(root, customModelData, 0);
   }

   private static ResourceKey selectModel(JsonElement element, Integer customModelData, int depth) {
      if (depth >= MAX_MODEL_DEPTH || element == null || !element.isJsonObject()) {
         return null;
      }

      JsonObject model = element.getAsJsonObject();
      String type = substringAfter(string(model, "type"), ':');
      if (type == null) {
         return null;
      }

      switch (type) {
         case "model":
            return ResourceKey.parse(string(model, "model"), "minecraft");
         case "range_dispatch": {
            JsonArray entries = array(model, "entries");
            JsonElement selected = null;
            if (customModelData != null && entries != null) {
               double best = Double.NEGATIVE_INFINITY;
               for (JsonElement entry : entries) {
                  if (!entry.isJsonObject()) {
                     continue;
                  }
                  JsonObject object = entry.getAsJsonObject();
                  JsonElement thresholdElement = object.get("threshold");
                  JsonElement child = object.get("model");
                  if (thresholdElement == null || child == null || !thresholdElement.isJsonPrimitive()) {
                     continue;
                  }
                  double threshold;
                  try {
                     threshold = thresholdElement.getAsDouble();
                  } catch (RuntimeException ignored) {
                     continue;
                  }
                  if (threshold <= customModelData && threshold >= best) {
                     best = threshold;
                     selected = child;
                  }
               }
            }
            JsonElement next = selected != null ? selected : model.get("fallback");
            return next == null ? null : selectModel(next, customModelData, depth + 1);
         }
         case "select": {
            JsonElement fallback = model.get("fallback");
            JsonElement firstCase = null;
            JsonArray cases = array(model, "cases");
            if (cases != null && cases.size() > 0 && cases.get(0).isJsonObject()) {
               firstCase = cases.get(0).getAsJsonObject().get("model");
            }
            JsonElement next = fallback != null ? fallback : firstCase;
            return next == null ? null : selectModel(next, customModelData, depth + 1);
         }
         case "condition": {
            JsonElement next = model.get("on_false");
            if (next == null) {
               next = model.get("on_true");
            }
            return next == null ? null : selectModel(next, customModelData, depth + 1);
         }
         case "composite": {
            JsonArray models = array(model, "models");
            if (models == null || models.size() == 0) {
               return null;
            }
            return selectModel(models.get(0), customModelData, depth + 1);
         }
         case "special": {
            JsonElement base = model.get("base");
            return base == null ? null : selectModel(base, customModelData, depth + 1);
         }
         default: {
            JsonElement nested = model.get("model");
            if (nested == null) {
               return null;
            }
            if (nested.isJsonPrimitive()) {
               return ResourceKey.parse(nested.getAsString(), "minecraft");
            }
            return selectModel(nested, customModelData, depth + 1);
         }
      }
   }

   private static ResourceKey resolveModelTexture(ZipFile zip, ResourceKey model) {
      Map<String, String> textures = loadModelTextures(zip, model, new LinkedHashSet<>(), 0);
      if (textures == null) {
         return null;
      }

      List<String> preferred = new java.util.ArrayList<>();
      preferred.add("layer0");
      preferred.add("particle");
      preferred.addAll(textures.keySet());

      Set<String> seen = new LinkedHashSet<>();
      for (String name : preferred) {
         if (!seen.add(name)) {
            continue;
         }
         String value = textures.get(name);
         if (value == null) {
            continue;
         }
         String resolved = resolveTextureVariable(value, textures, new LinkedHashSet<>());
         if (resolved == null) {
            continue;
         }
         ResourceKey key = ResourceKey.parse(resolved, "minecraft");
         if (key != null) {
            return key;
         }
      }

      return null;
   }

   private static Map<String, String> loadModelTextures(ZipFile zip, ResourceKey model, Set<ResourceKey> visited, int depth) {
      if (depth >= MAX_MODEL_DEPTH || !visited.add(model)) {
         return null;
      }

      JsonObject json = readJson(zip, "assets/" + model.namespace + "/models/" + model.path + ".json");
      if (json == null) {
         return new LinkedHashMap<>();
      }

      Map<String, String> result = new LinkedHashMap<>();
      ResourceKey parent = ResourceKey.parse(string(json, "parent"), "minecraft");
      if (parent != null) {
         Map<String, String> parentTextures = loadModelTextures(zip, parent, visited, depth + 1);
         if (parentTextures != null) {
            result.putAll(parentTextures);
         }
      }

      JsonObject textures = objectValue(json, "textures");
      if (textures != null) {
         for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
            JsonElement value = entry.getValue();
            if (value != null && value.isJsonPrimitive()) {
               result.put(entry.getKey(), value.getAsString());
            }
         }
      }

      return result;
   }

   private static String resolveTextureVariable(String value, Map<String, String> textures, Set<String> visited) {
      if (value == null || !value.startsWith("#")) {
         return value;
      }
      String variable = value.substring(1);
      if (!visited.add(variable)) {
         return null;
      }
      String next = textures.get(variable);
      return next == null ? null : resolveTextureVariable(next, textures, visited);
   }

   private static ItemSpriteReference resolveAtlas(ZipFile zip, ResourceKey texture, boolean preferBlocks) {
      java.util.List<ItemSpriteReference> matches = new java.util.ArrayList<>();
      var entries = zip.entries();
      while (entries.hasMoreElements()) {
         ZipEntry entry = entries.nextElement();
         Matcher matcher = ATLAS_PATH.matcher(entry.getName());
         if (!matcher.matches()) {
            continue;
         }

         ResourceKey atlas = new ResourceKey(matcher.group(1), matcher.group(2));
         JsonObject json = readJson(zip, entry.getName());
         if (json == null) {
            continue;
         }
         ResourceKey sprite = findSpriteInAtlas(json, atlas.namespace, texture);
         if (sprite != null) {
            matches.add(new ItemSpriteReference(atlas.toString(), sprite.toString()));
         }
      }

      if (!matches.isEmpty()) {
         String preferredPath;
         if (texture.path.startsWith("block/")) {
            preferredPath = "blocks";
         } else if (texture.path.startsWith("item/")) {
            preferredPath = "items";
         } else {
            preferredPath = preferBlocks ? "blocks" : "items";
         }

         for (ItemSpriteReference match : matches) {
            String atlasName = match.getAtlas().contains(":") ? match.getAtlas().substring(match.getAtlas().indexOf(':') + 1) : match.getAtlas();
            if (atlasName.equals(preferredPath)) {
               return match;
            }
         }
         return matches.get(0);
      }

      String atlas = texture.path.startsWith("block/") || preferBlocks ? "minecraft:blocks" : "minecraft:items";
      return ItemSpriteReference.of(atlas, texture.toString());
   }

   private static ResourceKey findSpriteInAtlas(JsonObject json, String defaultNamespace, ResourceKey texture) {
      JsonArray sources = array(json, "sources");
      if (sources == null) {
         return null;
      }

      for (JsonElement element : sources) {
         if (!element.isJsonObject()) {
            continue;
         }
         JsonObject source = element.getAsJsonObject();
         String type = substringAfter(string(source, "type"), ':');
         if (type == null) {
            continue;
         }

         switch (type) {
            case "single": {
               ResourceKey resource = ResourceKey.parse(string(source, "resource"), defaultNamespace);
               if (resource == null || !resource.equals(texture)) {
                  continue;
               }
               ResourceKey sprite = ResourceKey.parse(string(source, "sprite"), defaultNamespace);
               return sprite != null ? sprite : resource;
            }
            case "directory": {
               ResourceKey directory = ResourceKey.parse(string(source, "source"), defaultNamespace);
               if (directory == null || !directory.namespace.equals(texture.namespace)) {
                  continue;
               }
               String sourcePath = trimEnd(directory.path, '/');
               if (!texture.path.equals(sourcePath) && !texture.path.startsWith(sourcePath + "/")) {
                  continue;
               }
               String suffix = trimStart(texture.path.substring(sourcePath.length()), '/');
               String prefixRaw = string(source, "prefix");
               ResourceKey prefix = ResourceKey.parse(prefixRaw == null || prefixRaw.isEmpty() ? directory.path : prefixRaw, directory.namespace);
               if (prefix == null) {
                  continue;
               }
               String path = trimEnd(prefix.path, '/');
               String[] parts = path.isEmpty() ? new String[]{suffix} : (suffix.isEmpty() ? new String[]{path} : new String[]{path, suffix});
               return new ResourceKey(prefix.namespace, String.join("/", parts));
            }
            default:
         }
      }

      return null;
   }

   private static JsonObject readJson(ZipFile zip, String path) {
      ZipEntry entry = zip.getEntry(path);
      if (entry == null) {
         return null;
      }

      try (InputStreamReader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
         JsonElement parsed = new Gson().fromJson(reader, JsonElement.class);
         return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
      } catch (Throwable ignored) {
         return null;
      }
   }

   private static String string(JsonObject object, String key) {
      JsonElement element = object.get(key);
      return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
   }

   private static JsonArray array(JsonObject object, String key) {
      JsonElement element = object.get(key);
      return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
   }

   private static JsonObject objectValue(JsonObject object, String key) {
      JsonElement element = object.get(key);
      return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
   }

   private static String substringAfter(String value, char separator) {
      if (value == null) {
         return null;
      }
      int index = value.indexOf(separator);
      return index < 0 ? value : value.substring(index + 1);
   }

   private static String trimEnd(String value, char ch) {
      int end = value.length();
      while (end > 0 && value.charAt(end - 1) == ch) {
         end--;
      }
      return value.substring(0, end);
   }

   private static String trimStart(String value, char ch) {
      int start = 0;
      while (start < value.length() && value.charAt(start) == ch) {
         start++;
      }
      return value.substring(start);
   }

   private record ResourceKey(String namespace, String path) {
      @Override
      public String toString() {
         return this.namespace + ":" + this.path;
      }

      static ResourceKey parse(String raw, String defaultNamespace) {
         if (raw == null) {
            return null;
         }
         String value = raw.trim().toLowerCase();
         if (value.endsWith(".json")) {
            value = value.substring(0, value.length() - 5);
         }
         if (value.isEmpty() || value.startsWith("#")) {
            return null;
         }

         int separator = value.indexOf(':');
         String namespace = separator >= 0 ? value.substring(0, separator) : defaultNamespace;
         String path = separator >= 0 ? value.substring(separator + 1) : value;
         if (!KEY_PART.matcher(namespace).matches() || !PATH_PART.matcher(path).matches()) {
            return null;
         }
         return new ResourceKey(namespace, path);
      }
   }
}
