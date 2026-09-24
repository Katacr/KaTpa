package org.katacr.katpa.text;

import java.lang.reflect.Method;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 组件消息发送兼容层：Paper 优先直发 Adventure Component（保留 MiniMessage/字形/sprite），
 * 旧核心或 Spigot 回退到 legacy 序列化字符串。
 */
public final class AdventureSender {
   private AdventureSender() {
   }

   public static void sendMessage(CommandSender sender, Component message) {
      if (sender == null || message == null) {
         return;
      }

      Method method = findMethod(sender.getClass(), "sendMessage", Component.class);
      if (method != null) {
         try {
            method.setAccessible(true);
            method.invoke(sender, message);
            return;
         } catch (Throwable ignored) {
            // 回退
         }
      }
      sender.sendMessage(LegacyComponentSerializer.legacySection().serialize(message));
   }

   public static void sendActionBar(Player player, Component message) {
      if (player == null || message == null) {
         return;
      }

      Method method = findMethod(player.getClass(), "sendActionBar", Component.class);
      if (method != null) {
         try {
            method.setAccessible(true);
            method.invoke(player, message);
            return;
         } catch (Throwable ignored) {
            // 回退
         }
      }
      player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
            TextComponent.fromLegacyText(LegacyComponentSerializer.legacySection().serialize(message)));
   }

   private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
      try {
         return type.getMethod(name, parameterTypes);
      } catch (Throwable ignored) {
         return null;
      }
   }
}
