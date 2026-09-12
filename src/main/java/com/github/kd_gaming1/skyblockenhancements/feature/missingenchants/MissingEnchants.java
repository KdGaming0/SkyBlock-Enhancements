package com.github.kd_gaming1.skyblockenhancements.feature.missingenchants;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import com.github.kd_gaming1.skyblockenhancements.util.JsonLookup;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.*;
import java.util.function.ToIntFunction;

/**
 * Adds cached enchant warnings to SkyBlock tooltips. All mutable state belongs to the client thread.
 * A copied stack and the non-maxed setting identify the current analysis; publishing data discards
 * that analysis in full. Shift changes only rebuild presentation, never parse or resolve enchants.
 */
public final class MissingEnchants {
    private static final int MAX_LINE_WIDTH = 200;
    private static final String LIST_PREFIX = "› ";

    private final ToIntFunction<String> textWidth;

    MissingEnchants(ToIntFunction<String> textWidth) {
        this.textWidth = textWidth;
    }

    private final HoveredEnchantReader enchantReader = new HoveredEnchantReader();
    private MissingEnchantResolver resolver;
    private CachedItem cachedItem;

    private static final Set<String> ROMAN_NUMERALS = Set.of(
            "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"
    );

    public static void init() {
        MissingEnchants feature = new MissingEnchants(text -> Minecraft.getInstance().font.width(text));
        ItemTooltipCallback.EVENT.register(feature::onTooltip);
        EnchantDataLoader.register(feature::publishData);
    }

    /** Called on the client thread with a fully parsed snapshot. */
    void publishData(JsonLookup data) {
        resolver = new MissingEnchantResolver(data);
        cachedItem = null;
    }

    private void onTooltip(ItemStack stack, Item.TooltipContext ctx, TooltipFlag flag, List<Component> lines) {
        if (!SkyblockEnhancementsConfig.showMissingEnchantments || !HypixelLocationState.isOnHypixel()) return;
        boolean expanded = !SkyblockEnhancementsConfig.showWhenPressingShift || Minecraft.getInstance().hasShiftDown();
        appendTooltip(stack, lines, SkyblockEnhancementsConfig.showNotMaxedEnchantments, expanded);
    }

    void appendTooltip(ItemStack stack, List<Component> lines, boolean showNotMaxed, boolean expanded) {
        if (resolver == null || stack.isEmpty()) return;

        CachedItem item = cachedItem;
        if (item == null || item.showNotMaxed != showNotMaxed
                || !ItemStack.isSameItemSameComponents(stack, item.stack)) {
            HoveredEnchantReader.HoveredItemInfo hovered = enchantReader.readHoveredItemInfo(stack, lines);
            if (hovered == null) return;
            List<String> missing = resolver.findMissingEnchantNames(hovered.itemType(), hovered.currentEnchants().keySet());
            List<String> notMaxed = showNotMaxed
                    ? resolver.findNotMaxedEnchantNames(hovered.currentEnchants()) : List.of();
            item = new CachedItem(stack.copy(), showNotMaxed, hovered.currentEnchants(), missing, notMaxed);
            cachedItem = item;
        }

        // Empty results are cached too, and can never reuse another item's rendered block.
        if (item.missing.isEmpty() && item.notMaxed.isEmpty()) return;
        if (item.renderBlock == null || item.expanded != expanded) {
            item.renderBlock = expanded ? buildExpandedBlock(item.missing, item.notMaxed)
                    : buildCollapsedBlock(item.missing.size(), item.notMaxed.size());
            item.expanded = expanded;
        }
        lines.addAll(findInsertIndex(lines, item), item.renderBlock);
    }

    /** Owns analysis and presentation together, including placement for this stack's tooltip. */
    private static final class CachedItem {
        final ItemStack stack;
        final boolean showNotMaxed;
        final Map<String, Integer> currentEnchants;
        final List<String> missing;
        final List<String> notMaxed;
        List<Component> renderBlock;
        boolean expanded;
        List<String> normalizedTokens;
        int insertIndex = -1;

        CachedItem(ItemStack stack, boolean showNotMaxed, Map<String, Integer> currentEnchants,
                   List<String> missing, List<String> notMaxed) {
            this.stack = stack;
            this.showNotMaxed = showNotMaxed;
            this.currentEnchants = currentEnchants;
            this.missing = missing;
            this.notMaxed = notMaxed;
        }
    }

    private static List<Component> buildCollapsedBlock(int missingCount, int notMaxedCount) {
        List<Component> out = new ArrayList<>();
        out.add(Component.literal(""));
        if (missingCount > 0) {
            out.add(Component.literal("◆ Missing enchantments: " + missingCount + " (hold Shift)")
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        if (notMaxedCount > 0) {
            out.add(Component.literal("◆ Not maxed: " + notMaxedCount + " (hold Shift)")
                    .withStyle(ChatFormatting.GOLD));
        }
        return out;
    }

    private List<Component> buildExpandedBlock(List<String> missingNamesSorted, List<String> notMaxedNamesSorted) {
        List<Component> out = new ArrayList<>();

        if (!missingNamesSorted.isEmpty()) {
            out.add(Component.literal(""));
            out.add(Component.literal("◆ Missing enchantments:")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
            appendWrappedNames(out, missingNamesSorted, ChatFormatting.GRAY);
        }

        if (!notMaxedNamesSorted.isEmpty()) {
            out.add(Component.literal(""));
            out.add(Component.literal("◆ Not maxed enchantments:")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
            appendWrappedNames(out, notMaxedNamesSorted, ChatFormatting.YELLOW);
        }

        return out;
    }

    /** Word-wraps {@code names} onto lines fitting within {@link #MAX_LINE_WIDTH} pixels and appends them to {@code out}. */
    private void appendWrappedNames(List<Component> out, List<String> names, ChatFormatting color) {
        int commaWidth = textWidth.applyAsInt(", ");
        int prefixWidth = textWidth.applyAsInt(LIST_PREFIX);
        int maxWidth = MAX_LINE_WIDTH - prefixWidth;

        List<String> currentLine = new ArrayList<>();
        int currentWidth = 0;

        for (String name : names) {
            int nameWidth = textWidth.applyAsInt(name);
            int addWidth = currentLine.isEmpty() ? nameWidth : (commaWidth + nameWidth);

            if (!currentLine.isEmpty() && currentWidth + addWidth > maxWidth) {
                out.add(Component.literal(LIST_PREFIX + String.join(", ", currentLine))
                        .withStyle(color));
                currentLine.clear();
                currentWidth = 0;
                addWidth = nameWidth;
            }

            currentLine.add(name);
            currentWidth += addWidth;
        }

        if (!currentLine.isEmpty()) {
            out.add(Component.literal(LIST_PREFIX + String.join(", ", currentLine))
                    .withStyle(color));
        }
    }

    private static int findInsertIndex(List<Component> tooltipLines, CachedItem item) {
        if (item.insertIndex >= 0 && item.insertIndex <= tooltipLines.size()) return item.insertIndex;

        List<String> tokens = item.normalizedTokens;
        if (tokens == null) {
            tokens = new ArrayList<>(item.currentEnchants.size());
            for (String key : item.currentEnchants.keySet()) {
                String token = normalizeEnchantToken(key);
                if (!token.isEmpty()) tokens.add(token);
            }
            item.normalizedTokens = tokens;
        }

        int lastRomanLine   = -1;
        int lastEnchantLine = -1;

        for (int i = 0; i < tooltipLines.size(); i++) {
            String raw  = tooltipLines.get(i).getString();
            String line = raw.toLowerCase(Locale.ROOT);

            String trimmed = raw.stripTrailing();
            int spaceIdx = trimmed.lastIndexOf(' ');
            if (spaceIdx >= 0 && ROMAN_NUMERALS.contains(trimmed.substring(spaceIdx + 1))) {
                lastRomanLine = i;
                continue;
            }

            boolean isEnchantLine = line.contains("enchant");
            if (!isEnchantLine) {
                for (String token : tokens) {
                    if (line.contains(token)) {
                        isEnchantLine = true;
                        break;
                    }
                }
            }
            if (isEnchantLine) {
                lastEnchantLine = i;
            }
        }

        int anchor = lastRomanLine >= 0 ? lastRomanLine
                : lastEnchantLine >= 0 ? lastEnchantLine
                  : tooltipLines.size() - 1;

        int base = Math.min(anchor + 1, tooltipLines.size());

        for (int i = base; i < Math.min(tooltipLines.size(), base + 8); i++) {
            if (tooltipLines.get(i).getString().isEmpty()) {
                item.insertIndex = i;
                return i;
            }
        }
        item.insertIndex = base;
        return base;
    }

    private static String normalizeEnchantToken(String enchantId) {
        return enchantId.replace("ultimate_", "")
                .replace('_', ' ')
                .toLowerCase(Locale.ROOT);
    }

}
