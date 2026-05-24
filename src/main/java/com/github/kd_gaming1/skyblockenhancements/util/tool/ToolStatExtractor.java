package com.github.kd_gaming1.skyblockenhancements.util.tool;

import com.github.kd_gaming1.skyblockenhancements.util.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts arbitrary stats from an item's lore using high-performance parsing.
 * Eliminates regex matching on the hot path in favor of manual character scanning.
 */
public final class ToolStatExtractor {

    private ToolStatExtractor() {}

    private static final Pattern GENERIC_STAT_PATTERN = Pattern.compile(
            "([A-Za-z][A-Za-z\\s]{2,}?)\\s*[:+]\\s*([+\\-]?[\\d,.]+[kKmMbB]?)"
    );

    // ═══════════════════════════════════════════════════════════════════════════
    //  Core High-Performance Engine
    // ═══════════════════════════════════════════════════════════════════════════

    public record ParsedStats(double[] values, long mask) { }

    /**
     * One-pass extraction. Extracts all defined stats directly into a primitive buffer.
     */
    public static ParsedStats extractAll(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new ParsedStats(new double[ToolStat.VALUES.length], 0L);
        }

        double[] values = new double[ToolStat.VALUES.length];
        long mask = 0L;

        for (String line : getLoreLines(stack)) {
            String cleaned = StringUtil.stripColorCodes(line);

            for (ToolStat stat : ToolStat.VALUES) {
                if ((mask & (1L << stat.ordinal())) != 0) continue;

                double val = extractNumber(cleaned, stat);
                if (!Double.isNaN(val)) {
                    values[stat.ordinal()] = val;
                    mask |= (1L << stat.ordinal());
                    break;
                }
            }
        }
        return new ParsedStats(values, mask);
    }

    public static ParsedStats extractAllFromHeld() {
        return extractAll(getHeldItem());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Public Backwards Compat API
    // ═══════════════════════════════════════════════════════════════════════════

    public static OptionalInt fromHeldItem(ToolStat stat) {
        return fromItem(getHeldItem(), stat);
    }

    public static OptionalInt fromItem(ItemStack stack, ToolStat stat) {
        if (stack == null || stack.isEmpty()) return OptionalInt.empty();
        for (String line : getLoreLines(stack)) {
            String cleaned = StringUtil.stripColorCodes(line);
            double val = extractNumber(cleaned, stat);
            if (!Double.isNaN(val)) return OptionalInt.of((int) Math.round(val));
        }
        return OptionalInt.empty();
    }

    public static OptionalDouble fromHeldItemDouble(ToolStat stat) {
        return fromItemDouble(getHeldItem(), stat);
    }

    public static OptionalDouble fromItemDouble(ItemStack stack, ToolStat stat) {
        if (stack == null || stack.isEmpty()) return OptionalDouble.empty();
        for (String line : getLoreLines(stack)) {
            String cleaned = StringUtil.stripColorCodes(line);
            double val = extractNumber(cleaned, stat);
            if (!Double.isNaN(val)) return OptionalDouble.of(val);
        }
        return OptionalDouble.empty();
    }

    public static Optional<Map.Entry<String, String>> tryExtractGeneric(String loreLine) {
        if (loreLine == null || loreLine.isEmpty()) return Optional.empty();
        String cleaned = StringUtil.stripColorCodes(loreLine);

        Matcher m = GENERIC_STAT_PATTERN.matcher(cleaned);
        if (m.find()) {
            String key = m.group(1).trim();
            String value = m.group(2).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                return Optional.of(new AbstractMap.SimpleEntry<>(key, value));
            }
        }
        return Optional.empty();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Private Zero-Allocation String Scanners
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Checks if a line is a valid host for a specific stat and parses it.
     * Prevents overlapping false positives (e.g., "Speed" capturing "Mining Speed").
     */
    private static double extractNumber(String line, ToolStat stat) {
        for (String label : stat.loreLabels) {
            int idx = line.indexOf(label);
            if (idx != -1) {
                // Ensure we aren't matching a suffix (e.g., matching "Speed" inside "Mining Speed")
                if (idx > 0 && Character.isLetter(line.charAt(idx - 1))) {
                    continue;
                }

                // Scan forward from the end of the label to find the first number
                return parseNumberFrom(line, idx + label.length());
            }
        }
        return Double.NaN;
    }

    /**
     * Scans forward to build numeric boundaries and handles multipliers.
     */
    private static double parseNumberFrom(String line, int startIdx) {
        int start = -1;
        for (int i = startIdx; i < line.length(); i++) {
            char c = line.charAt(i);
            if (Character.isDigit(c) || c == '+' || c == '-') {
                start = i;
                break;
            }
        }
        if (start == -1) return Double.NaN;

        int end = start;
        boolean hasDecimal = false;
        char multiplier = 0;
        for (int i = start + 1; i < line.length(); i++) {
            char c = line.charAt(i);
            if (Character.isDigit(c) || c == ',') {
                end = i;
            } else if (!hasDecimal && c == '.') {
                hasDecimal = true;
                end = i;
            } else if (c == 'k' || c == 'K' || c == 'm' || c == 'M' || c == 'b' || c == 'B') {
                multiplier = Character.toLowerCase(c);
                end = i;
                break;
            } else {
                break; // Stop at spaces, parentheses, slashes, etc.
            }
        }

        String numStr = line.substring(start, end + 1).replace(",", "");
        if (multiplier != 0) {
            numStr = numStr.substring(0, numStr.length() - 1);
        }

        try {
            double val = Double.parseDouble(numStr);
            if (multiplier == 'k') val *= 1_000.0;
            else if (multiplier == 'm') val *= 1_000_000.0;
            else if (multiplier == 'b') val *= 1_000_000_000.0;
            return val;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    public static List<String> getLoreLines(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return List.of();

        List<Component> lines = lore.lines();
        if (lines.isEmpty()) return List.of();

        List<String> result = new ArrayList<>(lines.size());
        for (Component component : lines) {
            String text = component.getString();
            if (!text.isEmpty()) result.add(text);
        }
        return result;
    }

    private static ItemStack getHeldItem() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return ItemStack.EMPTY;
        return mc.player.getMainHandItem();
    }
}