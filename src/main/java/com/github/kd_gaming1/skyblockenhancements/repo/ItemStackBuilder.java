package com.github.kd_gaming1.skyblockenhancements.repo;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * Converts {@link NeuItem} POJOs into proper {@link ItemStack}s with custom name, lore, and item
 * model. Results are cached to avoid repeated allocations.
 *
 * <p>Skull textures are NOT fetched eagerly at build time. Instead, call
 * {@link #ensureSkinLoaded(ItemStack)} when a skull stack is first rendered or tooltipped.
 * This avoids scheduling thousands of main-thread skin fetches during bulk item construction.
 */
public final class ItemStackBuilder {

    private static final Map<String, ItemStack> CACHE = new ConcurrentHashMap<>();

    /**
     * Tracks skull stacks whose skin has already been fetched. Uses identity-based
     * comparison since all overlay stacks are cached singletons.
     */
    private static final Set<ItemStack> SKIN_LOADED = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Virtual items that don't exist in the NEU repo but are referenced in recipe data.
     */
    private static final Map<String, Supplier<ItemStack>> VIRTUAL_ITEMS =
            Map.of(
                    "SKYBLOCK_COIN",
                    ItemStackBuilder::named);

    private ItemStackBuilder() {}

    /** Returns a cached, immutable-ish prototype. Call {@code .copy()} before mutating. */
    public static ItemStack build(NeuItem item) {
        return CACHE.computeIfAbsent(item.internalName, k -> createStack(item));
    }

    /**
     * Builds an ingredient stack from a recipe reference like {@code "ENCHANTED_DIAMOND"}.
     */
    public static ItemStack buildIngredient(String neuId, int count) {
        if (neuId == null || neuId.isEmpty()) return ItemStack.EMPTY;

        NeuItem item = NeuItemRegistry.get(neuId);
        if (item != null) {
            ItemStack stack = build(item).copy();
            stack.setCount(count);
            return stack;
        }

        Supplier<ItemStack> virtual = VIRTUAL_ITEMS.get(neuId);
        if (virtual != null) {
            ItemStack stack = virtual.get();
            stack.setCount(count);
            return stack;
        }

        // Unknown item — show barrier with the ID as name
        ItemStack fallback = new ItemStack(Items.BARRIER, count);
        fallback.set(DataComponents.CUSTOM_NAME, Component.literal("§c" + neuId));
        CompoundTag tag = new CompoundTag();
        tag.putString("id", neuId);
        fallback.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return fallback;
    }

    /**
     * Lazily triggers the skin manager fetch for a skull stack. Call this from the
     * overlay slot render path or tooltip build — NOT during bulk construction.
     *
     * <p>No-op for non-skull stacks or stacks whose skin has already been fetched.
     */
    public static void ensureSkinLoaded(ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!stack.has(DataComponents.PROFILE)) return;
        if (!SKIN_LOADED.add(stack)) return; // already fetched

        ResolvableProfile profile = stack.get(DataComponents.PROFILE);
        if (profile == null) return;

        // The profile's GameProfile is needed for the skin manager.
        // ResolvableProfile wraps a GameProfile — fetch on the main thread.
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.getSkinManager().get(profile.partialProfile()));
    }

    /** Drops the entire stack cache — call after a repo reload. */
    public static void clearCache() {
        CACHE.clear();
        SKIN_LOADED.clear();
    }

    // ── Internal ────────────────────────────────────────────────────────────────

    private static ItemStack named() {
        ItemStack stack = new ItemStack(Items.GOLD_NUGGET);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("§6Coins"));
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "SKYBLOCK_COIN");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /**
     * Creates the stack without fetching skull textures. The profile is set so the
     * texture data is embedded in the stack, but the actual skin download is deferred
     * to {@link #ensureSkinLoaded(ItemStack)}.
     */
    private static ItemStack createStack(NeuItem item) {
        Item baseItem = resolveItem(item);
        ItemStack stack = new ItemStack(baseItem);

        // For enchanted books, use the enchant name (lore line 0) so RRV search
        // matches "Sharpness III" instead of just "Enchanted Book".
        String displayName = item.displayName;
        if (baseItem == Items.ENCHANTED_BOOK
                && item.lore != null && !item.lore.isEmpty()) {
            String enchantLine = item.lore.getFirst(); // e.g. "§9Sharpness VII"
            if (!enchantLine.isBlank()) {
                // Take the rarity color from displayName (e.g. "§5") and the text from lore line 0
                String rarityColor = item.displayName != null && item.displayName.length() > 2
                        && item.displayName.charAt(0) == '§'
                        ? item.displayName.substring(0, 2)
                        : "§f";
                String enchantText = enchantLine.length() > 2 && enchantLine.charAt(0) == '§'
                        ? enchantLine.substring(2)
                        : enchantLine;
                displayName = rarityColor + enchantText;
            }
        }

        stack.set(DataComponents.CUSTOM_NAME, Component.literal(displayName));

        CompoundTag customTag = new CompoundTag();
        customTag.putString("id", item.internalName);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customTag));

        if (item.lore != null && !item.lore.isEmpty()) {
            List<String> loreLines = (baseItem == Items.ENCHANTED_BOOK)
                    ? item.lore.subList(1, item.lore.size())
                    : item.lore;
            List<Component> lines = loreLines.stream().<Component>map(Component::literal).toList();
            stack.set(DataComponents.LORE, new ItemLore(lines));
        }

        if (item.itemModel != null) {
            Identifier modelRl = Identifier.tryParse(item.itemModel);
            if (modelRl != null) stack.set(DataComponents.ITEM_MODEL, modelRl);
        }

        if (item.skullTexture != null && !item.skullTexture.isEmpty()) {
            PropertyMap properties =
                    new PropertyMap(
                            ImmutableMultimap.of("textures", new Property("textures", item.skullTexture)));
            UUID uuid = UUID.nameUUIDFromBytes(item.skullTexture.getBytes(StandardCharsets.UTF_8));
            GameProfile profile = new GameProfile(uuid, item.internalName, properties);
            stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
            // Skin fetch is deferred — see ensureSkinLoaded().
        }

        if (item.leatherColor >= 0) {
            stack.set(DataComponents.DYED_COLOR, new DyedItemColor(item.leatherColor));
        }

        if (item.enchantmentGlint) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }

        return stack;
    }

    private static Item resolveItem(NeuItem item) {
        if (item.snbtItemId != null) {
            Item resolved = registryLookup(item.snbtItemId);
            if (resolved != Items.BARRIER) return resolved;
        }
        return registryLookup(mapLegacyId(item.itemId, item.damage));
    }

    private static Item registryLookup(String id) {
        Identifier rl = Identifier.tryParse(id);
        if (rl == null) return Items.BARRIER;
        Item resolved = BuiltInRegistries.ITEM.getValue(rl);
        return resolved != Items.AIR ? resolved : Items.BARRIER;
    }

    /**
     * Maps legacy (pre-1.13) item IDs + damage values to modern 1.21 equivalents.
     * Only called when no SNBT companion file exists.
     */
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
            case "minecraft:dye" -> switch (damage) {
                case 4 -> "minecraft:lapis_lazuli";
                case 6 -> "minecraft:cyan_dye";
                case 8 -> "minecraft:gray_dye";
                case 15 -> "minecraft:bone_meal";
                default -> "minecraft:ink_sac";
            };
            case "minecraft:banner" -> switch (damage) {
                case 0 -> "minecraft:black_banner";
                case 1 -> "minecraft:red_banner";
                case 2 -> "minecraft:green_banner";
                case 3 -> "minecraft:brown_banner";
                case 4 -> "minecraft:blue_banner";
                case 5 -> "minecraft:purple_banner";
                case 6 -> "minecraft:cyan_banner";
                case 7 -> "minecraft:light_gray_banner";
                case 8 -> "minecraft:gray_banner";
                case 9 -> "minecraft:pink_banner";
                case 10 -> "minecraft:lime_banner";
                case 11 -> "minecraft:yellow_banner";
                case 12 -> "minecraft:light_blue_banner";
                case 13 -> "minecraft:magenta_banner";
                case 14 -> "minecraft:orange_banner";
                default -> "minecraft:white_banner";
            };
            case "minecraft:noteblock"     -> "minecraft:note_block";
            case "minecraft:bed"           -> "minecraft:white_bed";
            case "minecraft:fish"          -> "minecraft:cod";
            case "minecraft:mob_spawner"   -> "minecraft:spawner";
            case "minecraft:monster_egg"   -> "minecraft:infested_stone";
            case "minecraft:tallgrass"     -> "minecraft:short_grass";
            case "minecraft:stained_glass_pane"   -> "minecraft:black_stained_glass_pane";
            case "minecraft:stained_hardened_clay" -> "minecraft:red_terracotta";
            case "minecraft:planks"        -> "minecraft:oak_planks";
            case "minecraft:potion"        -> "minecraft:potion";
            case "minecraft:spawn_egg"     -> "minecraft:bat_spawn_egg";
            default -> itemId;
        };
    }
}