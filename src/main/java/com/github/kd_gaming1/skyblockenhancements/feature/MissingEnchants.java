package com.github.kd_gaming1.skyblockenhancements.feature;

import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.util.JsonLookup;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class MissingEnchants {
    private static final Path storageRoot = FabricLoader.getInstance().getConfigDir().resolve("Skyblock Enhancements").resolve("data");
    private static final JsonLookup LOOKUP = new JsonLookup();
    private static final Path ENCHANTS_JSON = storageRoot.resolve("constants/enchants.json");

    private static final List<String> RARITIES = Arrays.asList(
            "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY SPECIAL", "ULTIMATE", "ADMIN"
    );

    private static final List<String> TYPES = Arrays.asList(
            "SWORD", "BOW", "AXE", "DRILL", "FISHING ROD", "FISHING WEAPON", "SHOVEL", "HOE", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS",
            "GAUNTLET", "GLOVES", "BELT", "NECKLACE", "BRACELET", "CLOAK", "CARNIVAL MASK"
    );

    public static void init() {
        ItemTooltipCallback.EVENT.register(MissingEnchants::onTooltip);
    }

    private static void onTooltip(ItemStack itemStack, Item.TooltipContext tooltipContext, TooltipFlag tooltipType, List<Component> texts) {
        if (!SkyblockEnhancementsConfig.showMissingEnchantments) return;
        if (!SkyblockEnhancements.helloPacketReceived.get()) return;

        String itemType = extractItemType(texts);
        List<String> currentEnchants = readEnchants(itemStack);

        if (itemType == null || currentEnchants.isEmpty()) return;
        if (currentEnchants.contains("one_for_all")) return;

        List<String> missing = findMissingEnchants(itemType, currentEnchants);
        if (missing.isEmpty()) return;

        int insertIndex = findInsertIndex(texts, currentEnchants);

        boolean shift;
        if (SkyblockEnhancementsConfig.showWhenPressingShift) {
            Minecraft client = Minecraft.getInstance();
            shift = client.hasShiftDown();
        } else {
            shift = true;
        }

        List<Component> toInsert = buildTooltipBlock(missing, shift);
        if (!toInsert.isEmpty()) {
            insertIndex = clamp(insertIndex, texts.size());
            texts.addAll(insertIndex, toInsert);
        }
    }

    private static String extractItemType(List<Component> texts) {
        for (int i = texts.size() - 1; i >= 0; i--) {
            String line = texts.get(i).getString().toUpperCase();

            for (String rarity : RARITIES) {
                int rarityIndex = line.indexOf(rarity);
                if (rarityIndex != -1) {
                    String afterRarity = line.substring(rarityIndex + rarity.length()).trim();

                    for (String type : TYPES) {
                        if (afterRarity.startsWith(type)) {
                            return type;
                        }
                    }

                    for (String type : TYPES) {
                        if (afterRarity.contains(type)) {
                            return type;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static List<String> readEnchants(ItemStack stack) {
        List<String> enchantments = new ArrayList<>();

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return enchantments;
        }

        CompoundTag nbt = customData.copyTag();
        if (!nbt.contains("enchantments")) {
            return enchantments;
        }

        CompoundTag enchants = nbt.getCompound("enchantments").orElse(null);
        if (enchants == null) {
            return enchantments;
        }

        enchantments.addAll(enchants.keySet());

        return enchantments;
    }

    private static List<String> findMissingEnchants(String itemType, List<String> enchants) {
        List<String> allEnchantsForType = LOOKUP.getEnchants(itemType, ENCHANTS_JSON);
        List<List<String>> enchantPools = LOOKUP.getEnchantPools(ENCHANTS_JSON);

        // Find missing enchants
        Set<String> currentSet = new HashSet<>(enchants);
        List<String> missing = allEnchantsForType.stream()
                .filter(e -> !currentSet.contains(e))
                .collect(Collectors.toList());

        // Remove conflicting enchants
        for (String currentEnchant : enchants) {
            for (List<String> pool : enchantPools) {
                if (pool.contains(currentEnchant)) {
                    // Remove all other enchants from this conflict pool
                    missing.removeAll(pool);
                    break;
                }
            }
        }

        return missing;
    }

    private static int findInsertIndex(List<Component> texts, List<String> currentEnchants) {
        int lastMatch = -1;

        List<String> tokens = new ArrayList<>(currentEnchants.size());
        for (String id : currentEnchants) {
            tokens.add(normalizeEnchantToken(id));
        }

        for (int i = 0; i < texts.size(); i++) {
            String line = texts.get(i).getString().toLowerCase(Locale.ROOT);

            if (line.contains("enchant")) {
                lastMatch = i;
                continue;
            }

            for (String token : tokens) {
                if (!token.isEmpty() && line.contains(token)) {
                    lastMatch = i;
                    break;
                }
            }
        }

        int base = (lastMatch >= 0) ? (lastMatch + 1) : texts.size();
        base = clamp(base, texts.size());

        // Insert before the next blank line
        int scanLimit = Math.min(texts.size(), base + 8);
        for (int i = base; i < scanLimit; i++) {
            if (texts.get(i).getString().isEmpty()) {
                return i;
            }
        }

        return base;
    }

    private static List<Component> buildTooltipBlock(List<String> missingIds, boolean shift) {
        List<Component> out = new ArrayList<>();

        out.add(Component.literal(""));

        if (!shift) {
            out.add(Component.literal("◆ Missing enchantments: " + missingIds.size() + " (hold Shift)")
                    .withStyle(ChatFormatting.DARK_AQUA));
            return out;
        }

        out.add(Component.literal("◆ Missing enchantments:")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));

        Minecraft client = Minecraft.getInstance();

        List<EnchantEntry> entries = new ArrayList<>(missingIds.size());
        for (String id : missingIds) {
            String formattedName = formatEnchantName(id);
            int width = client.font.width(formattedName);
            entries.add(new EnchantEntry(formattedName, width));
        }

        entries.sort(Comparator.comparing(e -> e.name, String.CASE_INSENSITIVE_ORDER));

        int maxWidth = 200;
        int commaWidth = client.font.width(", ");
        int prefixWidth = client.font.width("› ");
        int effectiveMaxWidth = maxWidth - prefixWidth;

        List<String> currentLine = new ArrayList<>();
        int currentWidth = 0;

        for (EnchantEntry entry : entries) {
            int addedWidth = currentLine.isEmpty()
                    ? entry.width
                    : commaWidth + entry.width;

            if (!currentLine.isEmpty() && currentWidth + addedWidth > effectiveMaxWidth) {
                // Flush current line
                out.add(Component.literal("› " + String.join(", ", currentLine))
                        .withStyle(ChatFormatting.GRAY));
                currentLine.clear();
                currentWidth = 0;
                addedWidth = entry.width;
            }

            currentLine.add(entry.name);
            currentWidth += addedWidth;
        }

        // Flush the remaining line
        if (!currentLine.isEmpty()) {
            out.add(Component.literal("› " + String.join(", ", currentLine))
                    .withStyle(ChatFormatting.GRAY));
        }

        return out;
    }

    private record EnchantEntry(String name, int width) {
    }

    private static String normalizeEnchantToken(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        s = s.replace("ultimate_", "");
        s = s.replace("turbo_", "turbo-");
        s = s.replace("_", " ");
        return s.trim();
    }

    private static String formatEnchantName(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        s = s.replace("ultimate_", "");
        s = s.replace("turbo_", "turbo-");
        s = s.replace("_", " ");
        s = titleCase(s);
        if ("pristine".equalsIgnoreCase(s)) s = "Prismatic";
        return s.trim();
    }

    private static String titleCase(String input) {
        if (input == null || input.isEmpty()) return input;

        String[] parts = input.split("\\s+");
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) continue;

            String word = Character.toUpperCase(p.charAt(0)) + p.substring(1);
            if (i > 0) sb.append(" ");
            sb.append(word);
        }

        return sb.toString();
    }

    private static int clamp(int v, int max) {
        if (v < 0) return 0;
        return Math.min(v, max);
    }
}