package com.devpilot.sandbox.guard;

import java.util.ArrayList;
import java.util.List;

/**
 * 命令行解析器，支持引号字符串和转义字符。
 * 例如: git commit -m "my message"  -> ["git", "commit", "-m", "my message"]
 */
public class CommandParser {

    public static String[] parse(String commandLine) {
        if (commandLine == null || commandLine.trim().isEmpty()) {
            return new String[0];
        }

        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < commandLine.length(); i++) {
            char c = commandLine.charAt(i);

            // Handle escape character
            if (c == '\\' && !inSingleQuote) {
                if (i + 1 < commandLine.length()) {
                    i++;
                    current.append(commandLine.charAt(i));
                }
                continue;
            }

            // Handle quotes
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }

            // Handle whitespace (only outside quotes)
            if (Character.isWhitespace(c) && !inSingleQuote && !inDoubleQuote) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }

            current.append(c);
        }

        // Don't forget the last token
        if (current.length() > 0) {
            parts.add(current.toString());
        }

        return parts.toArray(new String[0]);
    }

    /**
     * 从已解析的 tokens 中提取子命令（第二个 token）
     */
    public static String extractSubcommand(String[] parts) {
        return parts.length > 1 ? parts[1] : null;
    }

    /**
     * 检查 tokens 中是否包含某个 flag
     */
    public static boolean hasFlag(String[] parts, String flag) {
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].equals(flag)) {
                return true;
            }
        }
        return false;
    }
}
