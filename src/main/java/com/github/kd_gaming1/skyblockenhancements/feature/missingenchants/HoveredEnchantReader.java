package com.github.kd_gaming1.skyblockenhancements.feature.missingenchants;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

/**
 * Extracts the information needed to evaluate missing enchants from a hovered item.
 *
 * <p>Two things are read:
 * <ul>
 *   <li><b>Item type</b> — derived from the rarity/type line at the bottom of the tooltip
 *       (e.g. "LEGENDARY SWORD"). Only types listed in {@code SUPPORTED_TYPES} are considered
 *       enchantable; everything else returns {@code null} so the feature is skipped.</li>
 *   <li><b>Current enchants</b> — the keys of the {@code enchantments} compound inside the
 *       item's Skyblock custom NBT data.</li>
 * </ul>
 *
 * <p>Returns {@code null} from {@link #readHoveredItemInfo} for any item that should be ignored:
 * unsupported type, no enchants, or carrying One For All (which overrides normal enchant rules).
 */
final class HoveredEnchantReader {

    private static final Pattern TYPE_LINE = Pattern.compile(
            "(?:COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|VERY SPECIAL|SPECIAL)\\s+(?:DUNGEON\\s+)" +
                    "?(SWORD|BOW|AXE|PICKAXE|DRILL|FISHING ROD|FISHING WEAPON|SHOVEL|HOE|HELMET|CHESTPLATE|LEGGINGS|BOOTS|GAUNTLET|GLOVES|BELT|NECKLACE|BRACELET|CLOAK|CARNIVAL MASK)\\b"
    );

    HoveredItemInfo readHoveredItemInfo(ItemStack stack, List<Component> tooltipLines) {
        String itemType = readItemType(tooltipLines);
        if (itemType == null) return null;

        // One For All replaces all other enchants, so the normal "what's missing" logic doesn't apply.
        Map<String, Integer> currentEnchants = readCurrentEnchants(stack);
        if (currentEnchants.containsKey("one_for_all")) return null;

        return new HoveredItemInfo(itemType, currentEnchants);
    }

    private String readItemType(List<Component> tooltipLines) {
        // Scan bottom-up: the rarity line is always last in a Skyblock tooltip.
        for (int i = tooltipLines.size() - 1; i >= 0; i--) {
            String line = tooltipLines.get(i).getString();
            Matcher m = TYPE_LINE.matcher(line);
            if (!m.find()) continue;

            return m.group(1).trim().toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private Map<String, Integer> readCurrentEnchants(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return Map.of();
        return extractEnchantIds(customData);
    }

    private Map<String, Integer> extractEnchantIds(CustomData customData) {
        // LOGGER.info("Parsing enchants from NBT");
        CompoundTag tag = customData.copyTag();

        return tag.getCompound("enchantments").map(enchantments -> {
            Map<String, Integer> result = new HashMap<>();
            for (String key : enchantments.keySet()) {
                enchantments.getInt(key).ifPresent(level -> result.put(key, level));
            }
            return result;
        }).orElse(Map.of());
    }

    record HoveredItemInfo(String itemType, Map<String, Integer> currentEnchants) {}
}