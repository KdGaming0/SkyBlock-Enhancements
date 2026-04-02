package com.github.kd_gaming1.skyblockenhancements.repo;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.world.item.component.ResolvableProfile;
import java.util.UUID;

/**
 * Converts {@link NeuItem} POJOs into proper {@link ItemStack}s with custom name, lore, and
 * item model. Results are cached to avoid repeated allocations.
 */
public final class ItemStackBuilder {

    private static final Map<String, ItemStack> CACHE = new ConcurrentHashMap<>();

    private ItemStackBuilder() {}

    /** Returns a cached, immutable-ish prototype. Call {@code .copy()} before mutating. */
    public static ItemStack build(NeuItem item) {
        return CACHE.computeIfAbsent(item.internalName, k -> createStack(item));
    }

    /**
     * Builds an ingredient stack from a recipe reference like {@code "ENCHANTED_DIAMOND"}.
     * Looks up the full item in the registry for proper display; falls back to a barrier.
     */
    public static ItemStack buildIngredient(String neuId, int count) {
        if (neuId == null || neuId.isEmpty()) return ItemStack.EMPTY;

        NeuItem item = NeuItemRegistry.get(neuId);
        if (item != null) {
            ItemStack stack = build(item).copy();
            stack.setCount(count);
            return stack;
        }

        // Unknown item — show barrier with the ID as name
        ItemStack fallback = new ItemStack(Items.BARRIER, count);
        fallback.set(DataComponents.CUSTOM_NAME, Component.literal("§c" + neuId));
        return fallback;
    }

    /** Drops the entire stack cache — call after a repo reload. */
    public static void clearCache() {
        CACHE.clear();
    }

    // ── Internal ────────────────────────────────────────────────────────────────

    private static ItemStack createStack(NeuItem item) {
        Item baseItem = resolveItem(item.itemId, item.damage);
        ItemStack stack = new ItemStack(baseItem);

        // Custom name (§-formatted, rendered correctly by MC's font renderer)
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(item.displayName));

        // Lore
        if (item.lore != null && !item.lore.isEmpty()) {
            List<Component> lines = item.lore.stream()
                    .<Component>map(Component::literal)
                    .toList();
            stack.set(DataComponents.LORE, new ItemLore(lines));
        }

        // Item model (critical for texture-pack retexturing like FurfSky)
        if (item.itemModel != null) {
            Identifier modelRl = Identifier.tryParse(item.itemModel);
            if (modelRl != null) {
                stack.set(DataComponents.ITEM_MODEL, modelRl);
            }
        }

        // Apply custom player head texture
        if (item.skullTexture != null && !item.skullTexture.isEmpty()) {
            PropertyMap properties = new PropertyMap(
                    ImmutableMultimap.of("textures", new Property("textures", item.skullTexture))
            );

            UUID uuid = UUID.nameUUIDFromBytes(item.skullTexture.getBytes(StandardCharsets.UTF_8));
            GameProfile profile = new GameProfile(uuid, item.internalName, properties);
            stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));

            // Pre-warm the skin cache on the client thread
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> mc.getSkinManager().get(profile));
        }

        if (item.leatherColor >= 0) {
            stack.set(DataComponents.DYED_COLOR, new DyedItemColor(item.leatherColor));
        }

        return stack;
    }

    private static Item resolveItem(String itemId, int damage) {
        String mapped = mapLegacyId(itemId, damage);
        Identifier rl = Identifier.tryParse(mapped);
        if (rl == null) return Items.BARRIER;

        // DefaultedRegistry returns AIR for unknown keys
        Item resolved = BuiltInRegistries.ITEM.getValue(rl);
        return resolved != Items.AIR ? resolved : Items.BARRIER;
    }

    /** Maps pre-flattening item IDs + damage values to modern IDs. */
    private static String mapLegacyId(String itemId, int damage) {
        return switch (itemId) {
            case "minecraft:skull" -> switch (damage) {
                case 1 -> "minecraft:wither_skeleton_skull";
                case 2 -> "minecraft:zombie_head";
                case 3 -> "minecraft:player_head";
                case 4 -> "minecraft:creeper_head";
                case 5 -> "minecraft:dragon_head";
                default -> "minecraft:skeleton_skull";
            };
            case "minecraft:log" -> switch (damage) {
                case 1 -> "minecraft:spruce_log";
                case 2 -> "minecraft:birch_log";
                case 3 -> "minecraft:jungle_log";
                default -> "minecraft:oak_log";
            };
            case "minecraft:log2" -> damage == 1 ? "minecraft:dark_oak_log" : "minecraft:acacia_log";
            case "minecraft:wool" -> mapWoolColor(damage);
            case "minecraft:dye" -> mapDyeColor(damage);
            default -> itemId;
        };
    }

    private static String mapWoolColor(int damage) {
        return switch (damage) {
            case 1 -> "minecraft:orange_wool";
            case 2 -> "minecraft:magenta_wool";
            case 3 -> "minecraft:light_blue_wool";
            case 4 -> "minecraft:yellow_wool";
            case 5 -> "minecraft:lime_wool";
            case 6 -> "minecraft:pink_wool";
            case 7 -> "minecraft:gray_wool";
            case 8 -> "minecraft:light_gray_wool";
            case 9 -> "minecraft:cyan_wool";
            case 10 -> "minecraft:purple_wool";
            case 11 -> "minecraft:blue_wool";
            case 12 -> "minecraft:brown_wool";
            case 13 -> "minecraft:green_wool";
            case 14 -> "minecraft:red_wool";
            case 15 -> "minecraft:black_wool";
            default -> "minecraft:white_wool";
        };
    }

    private static String mapDyeColor(int damage) {
        return switch (damage) {
            case 1 -> "minecraft:red_dye";
            case 2 -> "minecraft:green_dye";
            case 3 -> "minecraft:cocoa_beans";
            case 4 -> "minecraft:lapis_lazuli";
            case 5 -> "minecraft:purple_dye";
            case 6 -> "minecraft:cyan_dye";
            case 7 -> "minecraft:light_gray_dye";
            case 8 -> "minecraft:gray_dye";
            case 9 -> "minecraft:pink_dye";
            case 10 -> "minecraft:lime_dye";
            case 11 -> "minecraft:yellow_dye";
            case 12 -> "minecraft:light_blue_dye";
            case 13 -> "minecraft:magenta_dye";
            case 14 -> "minecraft:orange_dye";
            case 15 -> "minecraft:bone_meal";
            default -> "minecraft:ink_sac";
        };
    }
}