package com.github.kd_gaming1.skyblockenhancements.util;

/**
 * General string utilities used across the mod.
 */
public final class StringUtil {

    private StringUtil() {}

    /**
     * Strips Minecraft color/formatting codes ({@code §x}) from a string.
     *
     * <p>Faster than {@code replaceAll("§.", "")} because it avoids regex compilation
     * and intermediate {@link String} objects.
     *
     * @param raw the input string, may be {@code null}
     * @return the cleaned string, or {@code ""} if {@code raw} is null or empty
     */
    public static String stripColorCodes(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        int len = raw.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char ch = raw.charAt(i);
            if (ch == '§' && i + 1 < len) {
                i++; // skip the formatting code character
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
