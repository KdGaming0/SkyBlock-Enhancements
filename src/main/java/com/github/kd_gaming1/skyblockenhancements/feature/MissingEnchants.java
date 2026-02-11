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

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.MOD_ID;

/**
 * Displays missing Skyblock enchantments on item tooltips.
 * <p>
 * Registers a tooltip callback (via {@code init()}), reads current enchants from item NBT,
 * compares them to enchants loaded from JSON using {@code JsonLookup} (path `constants/enchants.json`),
 * and inserts a compact tooltip block. Uses an LRU cache (`MISSING_CACHE`) and can be cleared
 * with {@code clearCache()}. Visibility respects {@code SkyblockEnhancementsConfig} and Shift.
 */
public class MissingEnchants {
    private static final Path storageRoot = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("data");
    private static final JsonLookup LOOKUP = new JsonLookup();
    private static final Path ENCHANTS_JSON = storageRoot.resolve("constants/enchants.json");

    // Shared upper bound for all LRU caches in this feature.
    private static final int CACHE_SIZE = 256;
    private static final Map<CacheKey, List<String>> MISSING_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<CacheKey, List<String>> eldest) {
                    return size() > CACHE_SIZE;
                }
            }
    );
    private static final Map<TooltipBlockKey, List<Component>> TOOLTIP_BLOCK_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<TooltipBlockKey, List<Component>> eldest) {
                    return size() > CACHE_SIZE;
                }
            }
    );
    // Caches fully built tooltip blocks to skip sorting/wrapping and font width calls per frame.
    // Caches normalized enchant tokens used during insert-position lookup.
    private static final Map<CacheKey, List<String>> ENCHANT_TOKEN_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<CacheKey, List<String>> eldest) {
                    return size() > CACHE_SIZE;
                }
            }
    );
    // Identity cache for CustomData to avoid copyTag()/NBT parsing every render pass.
    private static final IdentityHashMap<CustomData, List<String>> ENCHANTS_BY_CUSTOM_DATA = new IdentityHashMap<>();
    private static final int CUSTOM_DATA_CACHE_SIZE = CACHE_SIZE * 2;

    private static final List<String> RARITIES = Arrays.asList(
            "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY SPECIAL", "ULTIMATE", "ADMIN"
    );

    private static final List<String> TYPES = Arrays.asList(
            "SWORD", "BOW", "AXE", "PICKAXE", "DRILL", "FISHING ROD", "FISHING WEAPON", "SHOVEL", "HOE", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS",
            "GAUNTLET", "GLOVES", "BELT", "NECKLACE", "BRACELET", "CLOAK", "CARNIVAL MASK"
    );

    public static void init() {
        ItemTooltipCallback.EVENT.register(MissingEnchants::onTooltip);
    }

    public static void clearCache() {
        // Keep all memoized values in sync when the feature cache is reset by command/config flow.
        MISSING_CACHE.clear();
        TOOLTIP_BLOCK_CACHE.clear();
        ENCHANT_TOKEN_CACHE.clear();
        synchronized (ENCHANTS_BY_CUSTOM_DATA) {
            ENCHANTS_BY_CUSTOM_DATA.clear();
        }
    }

    private static void onTooltip(ItemStack itemStack, Item.TooltipContext tooltipContext, TooltipFlag tooltipType, List<Component> texts) {
        // Called on tooltip build/render; keep this path allocation-light.
        if (!SkyblockEnhancementsConfig.showMissingEnchantments) return;
        if (!SkyblockEnhancements.helloPacketReceived.get()) return;

        String itemType = extractItemType(texts);
        List<String> currentEnchants = readEnchants(itemStack);

        if (itemType == null || currentEnchants.isEmpty()) return;
        for (String e : currentEnchants) {
            if ("one_for_all".equalsIgnoreCase(e)) return;
        }

        CacheKey cacheKey = CacheKey.from(itemType, currentEnchants);
        List<String> missing = MISSING_CACHE.get(cacheKey);
        if (missing == null) {
            missing = List.copyOf(findMissingEnchants(itemType, currentEnchants));
            MISSING_CACHE.put(cacheKey, missing);
        }
        if (missing.isEmpty()) return;

        List<String> enchantTokens = getEnchantTokens(cacheKey, currentEnchants);
        int insertIndex = findInsertIndex(texts, enchantTokens);

        boolean shift;
        if (SkyblockEnhancementsConfig.showWhenPressingShift) {
            Minecraft client = Minecraft.getInstance();
            shift = client.hasShiftDown();
        } else {
            shift = true;
        }

        List<Component> toInsert = getTooltipBlock(cacheKey, missing, shift);
        if (!toInsert.isEmpty()) {
            insertIndex = clamp(insertIndex, texts.size());
            texts.addAll(insertIndex, toInsert);
        }
    }

    private static List<Component> getTooltipBlock(CacheKey cacheKey, List<String> missingIds, boolean shift) {
        // Final rendered lines only depend on enchant set + shift state.
        TooltipBlockKey tooltipKey = new TooltipBlockKey(cacheKey, shift);
        List<Component> cached = TOOLTIP_BLOCK_CACHE.get(tooltipKey);
        if (cached != null) {
            return cached;
        }

        List<Component> built = buildTooltipBlock(missingIds, shift);
        List<Component> immutable = List.copyOf(built);
        TOOLTIP_BLOCK_CACHE.put(tooltipKey, immutable);
        return immutable;
    }

    private static List<String> getEnchantTokens(CacheKey cacheKey, List<String> currentEnchants) {
        // Token normalization is reused by findInsertIndex and does not change per frame.
        List<String> cached = ENCHANT_TOKEN_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<String> tokens = new ArrayList<>(currentEnchants.size());
        for (String id : currentEnchants) {
            tokens.add(normalizeEnchantToken(id));
        }

        List<String> immutable = List.copyOf(tokens);
        ENCHANT_TOKEN_CACHE.put(cacheKey, immutable);
        return immutable;
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
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return List.of();
        }

        synchronized (ENCHANTS_BY_CUSTOM_DATA) {
            List<String> cached = ENCHANTS_BY_CUSTOM_DATA.get(customData);
            if (cached != null) {
                return cached;
            }
        }

        CompoundTag nbt = customData.copyTag();
        if (!nbt.contains("enchantments")) {
            return cacheCustomDataEnchants(customData, List.of());
        }

        CompoundTag enchants = nbt.getCompound("enchantments").orElse(null);
        if (enchants == null) {
            return cacheCustomDataEnchants(customData, List.of());
        }

        return cacheCustomDataEnchants(customData, List.copyOf(enchants.keySet()));
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

    private static List<String> cacheCustomDataEnchants(CustomData customData, List<String> enchantments) {
        synchronized (ENCHANTS_BY_CUSTOM_DATA) {
            ENCHANTS_BY_CUSTOM_DATA.put(customData, enchantments);
            // Cheap bounded strategy: clear whole identity cache once it grows too much.
            if (ENCHANTS_BY_CUSTOM_DATA.size() > CUSTOM_DATA_CACHE_SIZE) {
                ENCHANTS_BY_CUSTOM_DATA.clear();
            }
        }
        return enchantments;
    }

    private static int findInsertIndex(List<Component> texts, List<String> enchantTokens) {
        int lastMatch = -1;

        for (int i = 0; i < texts.size(); i++) {
            String line = texts.get(i).getString().toLowerCase(Locale.ROOT);

            if (line.contains("enchant")) {
                lastMatch = i;
                continue;
            }

            for (String token : enchantTokens) {
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

    private record CacheKey(String itemType, String enchantKey) {
        private static CacheKey from(String itemType, List<String> enchants) {
            List<String> sorted = new ArrayList<>(enchants);
            sorted.sort(String.CASE_INSENSITIVE_ORDER);
            return new CacheKey(itemType, String.join(",", sorted));
        }
    }

    private record TooltipBlockKey(CacheKey cacheKey, boolean shift) {
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
