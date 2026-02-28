package com.github.kd_gaming1.skyblockenhancements.feature.missingenchants;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import com.github.kd_gaming1.skyblockenhancements.util.JsonLookup;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.nio.file.Path;
import java.util.*;

// import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;
import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.MOD_ID;

/**
 * Appends a "Missing enchantments" section to the tooltip of any enchantable Skyblock item.
 *
 * <p>For each hovered item is the feature:
 * <ol>
 *   <li>Reads the item type and current enchants from the tooltip / NBT via {@link HoveredEnchantReader}.</li>
 *   <li>Compares the current enchants against the full list for that item type (loaded from
 *       {@code enchants.json}) via {@link MissingEnchantResolver}, respecting mutually exclusive
 *       enchant pools so that only one enchant per pool is ever counted as missing.</li>
 *   <li>Injects the resulting list into the tooltip, either as a compact count (default) or a
 *       full expanded list (while Shift is held), positioned just after the existing enchant lines.</li>
 * </ol>
 *
 * <p><b>Caching:</b> {@code onTooltip} is called every frame for the hovered item, so three layers
 * of caching keep it cheap:
 * <ul>
 *   <li><b>Identity cache</b> — if the same {@link ItemStack} object is seen again, skip all NBT
 *       parsing and jump straight to inserting (or rebuilding if Shift changed).</li>
 *   <li><b>Item cache</b> — if the item type and enchant set are unchanged, skip the resolver.</li>
 *   <li><b>Render cache</b> — if neither the missing list nor the expanded state changed, reuse
 *       the already-built {@link Component} list.</li>
 * </ul>
 * Items without enchants (e.g. plain tools, consumables) are ignored and do <em>not</em> update
 * the identity cache, so mousing back to a previously analysed enchanted item still hits the fast path.
 */
public final class MissingEnchants {

    private static final Path DATA_ROOT = FabricLoader.getInstance().getConfigDir()
            .resolve(MOD_ID).resolve("data");
    private static final Path ENCHANTS_JSON_PATH = DATA_ROOT.resolve("constants/enchants.json");

    private static final int MAX_LINE_WIDTH = 200;
    private static final String LIST_PREFIX = "› ";

    private static final HoveredEnchantReader ENCHANT_READER = new HoveredEnchantReader();
    private static final MissingEnchantResolver MISSING_RESOLVER =
            new MissingEnchantResolver(new JsonLookup(), ENCHANTS_JSON_PATH);

    private static final Set<String> ROMAN_NUMERALS = Set.of(
            "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"
    );

    // --- Item cache: what was the last enchantable item we resolved? ---
    private static String lastItemType;
    private static Map<String, Integer> lastCurrentEnchants = Map.of();
    private static List<String> lastMissingNamesSorted = List.of();
    private static List<String> lastNotMaxedNamesSorted = List.of();

    // --- Render cache: the built Component list, valid while the missing list and expanded state are unchanged ---
    private static List<String> lastRenderedForMissing = List.of();
    private static boolean lastRenderedExpanded;
    private static List<Component> lastRenderBlock = List.of();

    // --- Identity cache: the last ItemStack object we successfully analysed ---
    private static ItemStack lastStack = ItemStack.EMPTY;

    public static void init() {
        ItemTooltipCallback.EVENT.register(MissingEnchants::onTooltip);
    }

    private static void onTooltip(ItemStack stack, Item.TooltipContext ctx, TooltipFlag flag, List<Component> tooltipLines) {
        if (!SkyblockEnhancementsConfig.showMissingEnchantments) return;
        if (!HypixelLocationState.isOnHypixel()) return;

        boolean expanded = !SkyblockEnhancementsConfig.showWhenPressingShift || Minecraft.getInstance().hasShiftDown();

        // Identity fast path: the same ItemStack object means the item hasn't changed.
        if (!lastStack.isEmpty() && ItemStack.isSameItemSameComponents(stack, lastStack)) {
            if (expanded == lastRenderedExpanded) {
                // Nothing changed — just re-insert the cached block.
                if (!lastRenderBlock.isEmpty()) insertBlock(tooltipLines, lastRenderBlock, lastCurrentEnchants);
                return;
            }
            // Only Shift changed — rebuild the block without reparsing NBT.
            if (!lastMissingNamesSorted.isEmpty() || !lastNotMaxedNamesSorted.isEmpty()) {
                // LOGGER.info("Rebuilding tooltip block (expanded={})", expanded);
                lastRenderBlock = expanded
                        ? buildExpandedBlock(lastMissingNamesSorted, lastNotMaxedNamesSorted)
                        : buildCollapsedBlock(lastMissingNamesSorted.size(), lastNotMaxedNamesSorted.size());
                lastRenderedExpanded = expanded;
                lastRenderedForMissing = lastMissingNamesSorted;
                insertBlock(tooltipLines, lastRenderBlock, lastCurrentEnchants);
            }
            return;
        }

        // New stack — parse it. If the item isn't enchantable, we return without updating the lastStack
        // so that mousing back to the previous enchanted item still hits the identity fast path above.
        HoveredEnchantReader.HoveredItemInfo hovered = ENCHANT_READER.readHoveredItemInfo(stack, tooltipLines);
        if (hovered == null) return;

        if (!isSameItem(hovered)) {
            // LOGGER.info("Recomputing missing enchants for {} with enchants {}", hovered.itemType(), hovered.currentEnchants());
            lastMissingNamesSorted = MISSING_RESOLVER.findMissingEnchantNames(
                    hovered.itemType(), hovered.currentEnchants().keySet());

            if (SkyblockEnhancementsConfig.showNotMaxedEnchantments) {
                lastNotMaxedNamesSorted = MISSING_RESOLVER.findNotMaxedEnchantNames(
                        hovered.itemType(), hovered.currentEnchants());
            } else {
                lastNotMaxedNamesSorted = List.of();
            }

            lastItemType = hovered.itemType();
            lastCurrentEnchants = hovered.currentEnchants();
            lastRenderedForMissing = List.of(); // invalidate render cache for the new item
        }

        // Commit the identity cache only after confirming this is an enchantable item.
        lastStack = stack.copy();

        if (lastMissingNamesSorted.isEmpty() && lastNotMaxedNamesSorted.isEmpty()) return;

        // Rebuild the rendered Component list only when content or view mode changed.
        if (!Objects.equals(lastMissingNamesSorted, lastRenderedForMissing) || expanded != lastRenderedExpanded) {
            // LOGGER.info("Rebuilding tooltip block (expanded={})", expanded);
            lastRenderBlock = expanded
                    ? buildExpandedBlock(lastMissingNamesSorted, lastNotMaxedNamesSorted)
                    : buildCollapsedBlock(lastMissingNamesSorted.size(), lastNotMaxedNamesSorted.size());
            lastRenderedForMissing = lastMissingNamesSorted;
            lastRenderedExpanded = expanded;
        }

        insertBlock(tooltipLines, lastRenderBlock, lastCurrentEnchants);
    }

    private static boolean isSameItem(HoveredEnchantReader.HoveredItemInfo hovered) {
        return Objects.equals(hovered.itemType(), lastItemType)
                && hovered.currentEnchants().equals(lastCurrentEnchants);
    }

    private static List<Component> buildCollapsedBlock(int missingCount, int notMaxedCount) {
        List<Component> out = new ArrayList<>();
        out.add(Component.literal(""));
        if (missingCount > 0) {
            out.add(Component.literal("◆ Missing enchantments: " + missingCount + " (hold Shift)")
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        if (SkyblockEnhancementsConfig.showNotMaxedEnchantments && notMaxedCount > 0) {
            out.add(Component.literal("◆ Not maxed: " + notMaxedCount + " (hold Shift)")
                    .withStyle(ChatFormatting.GOLD));
        }
        return out;
    }

    private static List<Component> buildExpandedBlock(List<String> missingNamesSorted, List<String> notMaxedNamesSorted) {
        Minecraft mc = Minecraft.getInstance();

        List<Component> out = new ArrayList<>();

        if (!missingNamesSorted.isEmpty()) {
            out.add(Component.literal(""));
            out.add(Component.literal("◆ Missing enchantments:")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
            appendWrappedNames(mc, out, missingNamesSorted, ChatFormatting.GRAY);
        }

        if (SkyblockEnhancementsConfig.showNotMaxedEnchantments && !notMaxedNamesSorted.isEmpty()) {
            out.add(Component.literal(""));
            out.add(Component.literal("◆ Not maxed enchantments:")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
            appendWrappedNames(mc, out, notMaxedNamesSorted, ChatFormatting.YELLOW);
        }

        return out;
    }

    /** Word-wraps {@code names} onto lines fitting within {@link #MAX_LINE_WIDTH} pixels and appends them to {@code out}. */
    private static void appendWrappedNames(Minecraft mc, List<Component> out, List<String> names, ChatFormatting color) {
        int commaWidth = mc.font.width(", ");
        int prefixWidth = mc.font.width(LIST_PREFIX);
        int maxWidth = MAX_LINE_WIDTH - prefixWidth;

        List<String> currentLine = new ArrayList<>();
        int currentWidth = 0;

        for (String name : names) {
            int nameWidth = mc.font.width(name);
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

    private static void insertBlock(List<Component> tooltipLines, List<Component> block, Map<String, Integer> currentEnchants) {
        tooltipLines.addAll(findInsertIndex(tooltipLines, currentEnchants.keySet()), block);
    }

    private static int findInsertIndex(List<Component> tooltipLines, Set<String> enchantKeys) {
        List<String> tokens = enchantKeys.stream()
                .map(MissingEnchants::normalizeEnchantToken)
                .filter(t -> !t.isEmpty())
                .toList();

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

            if (line.contains("enchant") || tokens.stream().anyMatch(line::contains)) {
                lastEnchantLine = i;
            }
        }

        int anchor = lastRomanLine >= 0 ? lastRomanLine
                : lastEnchantLine >= 0 ? lastEnchantLine
                : tooltipLines.size() - 1;

        int base = Math.min(anchor + 1, tooltipLines.size());

        for (int i = base; i < Math.min(tooltipLines.size(), base + 8); i++) {
            if (tooltipLines.get(i).getString().isEmpty()) return i;
        }
        return base;
    }

    private static String normalizeEnchantToken(String enchantId) {
        return enchantId.replace("ultimate_", "")
                .replace('_', ' ')
                .toLowerCase(Locale.ROOT);
    }

    /** Clears all caches — called when the repo data is reloaded from disk. */
    public static void invalidateRepoDataCaches() {
        MISSING_RESOLVER.clearCaches();

        lastItemType = null;
        lastCurrentEnchants = Map.of();
        lastMissingNamesSorted = List.of();
        lastNotMaxedNamesSorted = List.of();

        lastRenderedForMissing = List.of();
        lastRenderedExpanded = false;
        lastRenderBlock = List.of();
    }
}