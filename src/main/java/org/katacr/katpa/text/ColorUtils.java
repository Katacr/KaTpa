package org.katacr.katpa.text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.md_5.bungee.api.ChatColor;

public class ColorUtils {
   public static final String WITH_DELIMITER = "((?<=%1$s)|(?<=%1$s))";

   public static String translateColorCodes(String text) {
      Pattern hexPattern = Pattern.compile("(&#)([0-9a-fA-F]{6})");
      Matcher matcher = hexPattern.matcher(text);
      StringBuffer buffer = new StringBuffer();

      while (matcher.find()) {
         String hex = matcher.group(2);
         String colorCode = ChatColor.of("#" + hex).toString();
         matcher.appendReplacement(buffer, colorCode);
      }

      matcher.appendTail(buffer);
      return ChatColor.translateAlternateColorCodes('&', buffer.toString());
   }

   public static List<String> translateColorCodes(List<String> lines) {
      List<String> coloredLines = new ArrayList<>();

      for (String line : lines) {
         coloredLines.add(translateColorCodes(line));
      }

      return coloredLines;
   }
}
