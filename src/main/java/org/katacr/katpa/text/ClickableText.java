package org.katacr.katpa.text;

import java.time.Duration;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;

/**
 * 可点击文本构建与发送工具。
 *
 * <p>使用 Adventure 的 {@link ClickEvent#callback} 自定义回调，点击时在服务端触发回调，
 * 不会像 {@code runCommand} 那样在客户端弹出“确定执行指令”二次确认。
 *
 * <p>{@code callback} 依赖 Paper 的核心支持；若当前核心不支持（旧 Spigot），
 * {@link #sendClickable} 会退化为发送纯文本并返回 {@code false}，由调用方决定降级策略。
 */
public final class ClickableText {
   private ClickableText() {
   }

   /** 点击回调执行体，接收点击的玩家。 */
   @FunctionalInterface
   public interface Handler {
      void run(Player player);
   }

   /** 构建结果：组件与是否成功附加了回调。 */
   public record Result(Component component, boolean clickable) {
   }

   /**
    * 发送带自定义点击回调的文本；文本中以 {@code [ ]} 包裹的部分作为可点击区域，
    * 其余部分为普通文本。若没有 {@code [ ]} 则整段可点击。
    *
    * @return 是否成功附加了回调（false 表示核心不支持，已退化为纯文本发送）
    */
   public static boolean sendClickable(Player player, String text, Handler handler, Duration lifetime) {
      if (player == null) {
         return false;
      }
      Result result = build(text, handler, lifetime);
      AdventureSender.sendMessage(player, result.component());
      return result.clickable();
   }

   /** 构建可点击组件：仅 {@code [ ]} 内的文字附加回调，其余保持普通样式。 */
   public static Result build(String text, Handler handler, Duration lifetime) {
      if (text == null || text.isEmpty()) {
         return new Result(Component.empty(), false);
      }
      int open = text.indexOf('[');
      int close = open < 0 ? -1 : text.indexOf(']', open + 1);
      if (open < 0 || close < 0) {
         Result whole = apply(TextParser.parse(text), handler, lifetime);
         return whole;
      }
      Component before = TextParser.parse(text.substring(0, open));
      Result link = apply(TextParser.parse(text.substring(open, close + 1)), handler, lifetime);
      Component after = TextParser.parse(text.substring(close + 1));
      return new Result(before.append(link.component()).append(after), link.clickable());
   }

   /**
    * 按点击区域序号（从 0 开始）与区域文字解析对应回调；返回 {@code null} 表示该区域不可点击。
    *
    * <p>建议按序号分派（而非文字），这样翻译文本变化不会影响回调绑定。
    */
   @FunctionalInterface
   public interface HandlerResolver {
      Handler resolve(int index, String label);
   }

   /**
    * 发送含多个可点击区域 {@code [ ]} 的文本，各区域由 {@link HandlerResolver} 按文字分派回调。
    *
    * @return 是否至少有一个区域成功附加了回调
    */
   public static boolean sendClickableMulti(Player player, String text, HandlerResolver resolver, Duration lifetime) {
      if (player == null) {
         return false;
      }
      Result result = buildMulti(text, resolver, lifetime);
      AdventureSender.sendMessage(player, result.component());
      return result.clickable();
   }

   /** 构建含多个可点击区域的组件；未被 resolver 命中或核心不支持时该区域保持普通文本。 */
   public static Result buildMulti(String text, HandlerResolver resolver, Duration lifetime) {
      if (text == null || text.isEmpty()) {
         return new Result(Component.empty(), false);
      }
      Component result = Component.empty();
      boolean anyClickable = false;
      int cursor = 0;
      int index = 0;
      while (cursor < text.length()) {
         int open = text.indexOf('[', cursor);
         if (open < 0) {
            result = result.append(TextParser.parse(text.substring(cursor)));
            break;
         }
         int close = text.indexOf(']', open + 1);
         if (close < 0) {
            result = result.append(TextParser.parse(text.substring(cursor)));
            break;
         }
         if (open > cursor) {
            result = result.append(TextParser.parse(text.substring(cursor, open)));
         }
         String label = text.substring(open, close + 1);
         Handler handler = resolver == null ? null : resolver.resolve(index, label);
         Result segment = apply(TextParser.parse(label), handler, lifetime);
         result = result.append(segment.component());
         anyClickable |= segment.clickable();
         index++;
         cursor = close + 1;
      }
      return new Result(result, anyClickable);
   }

   /** 为组件附加自定义点击回调；核心不支持回调时返回原组件与 false。 */
   private static Result apply(Component component, Handler handler, Duration lifetime) {
      if (component == null || handler == null) {
         return new Result(component, false);
      }
      try {
         ClickCallback.Options options = ClickCallback.Options.builder()
               .uses(1)
               .lifetime(lifetime)
               .build();
         ClickEvent event = ClickEvent.callback((Audience audience) -> {
            if (audience instanceof Player player) {
               handler.run(player);
            }
         }, options);
         return new Result(component.clickEvent(event), true);
      } catch (Throwable ignored) {
         // 核心不支持 ClickEvent.callback（旧 Spigot）时退化为无点击事件。
         return new Result(component, false);
      }
   }
}
