package com.github.kd_gaming1.skyblockenhancements.repo.item;

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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;

/**
 * Converts {@link NeuItem} POJOs into rendered {@link ItemStack}s.
 *
 * <p>Stacks are cached as immutable prototypes — callers copy before mutating. Skull textures are
 * embedded at build time but the skin download is deferred to {@link #ensureSkinLoaded}, avoiding
 * thousands of main-thread fetches during bulk construction.
 */
public final class ItemStackBuilder {

    /** Prototype cache. Stacks here are shared; call {@code .copy()} before mutating. */
    private static final Map<String, ItemStack> CACHE = new ConcurrentHashMap<>();

    /** Identity set of skull stacks whose skin fetch has already been scheduled. */
    private static final Set<ItemStack> SKIN_LOADED =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** Recipe-only virtual items that don't have a corresponding NEU repo entry. */
    private static final Map<String, Supplier<ItemStack>> VIRTUAL_ITEMS = Map.of(
            "SKYBLOCK_COIN", ItemStackBuilder::buildCoinStack
    );

    private ItemStackBuilder() {}

    // ── Public API ───────────────────────────────────────────────────────────────

    /** Returns a shared prototype for the given item. Call {@code .copy()} before mutating. */
    public static ItemStack build(NeuItem item) {
        return CACHE.computeIfAbsent(item.internalName, k -> createStack(item));
    }

    /**
     * Builds an ingredient stack from a recipe reference like {@code "ENCHANTED_DIAMOND"}.
     * Resolves against NEU items first, falling back to virtual items, then a visible barrier.
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

        return buildUnknownFallback(neuId, count);
    }

    /**
     * Triggers the skin fetch for a skull stack. Safe to call from a render or tooltip path.
     * No-op for non-skull stacks and for stacks that have already been fetched this session.
     */
    public static void ensureSkinLoaded(ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!stack.has(DataComponents.PROFILE)) return;
        if (!SKIN_LOADED.add(stack)) return;

        ResolvableProfile profile = stack.get(DataComponents.PROFILE);
        if (profile == null) return;

        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.getSkinManager().get(profile.partialProfile()));
    }

    /** Drops all cached prototypes — call after a repo reload. */
    public static void clearCache() {
        CACHE.clear();
        SKIN_LOADED.clear();
    }

    // ── Stack construction ──────────────────────────────────────────────────────

    private static ItemStack createStack(NeuItem item) {
        Item baseItem = resolveBaseItem(item);
        ItemStack stack = new ItemStack(baseItem);

        applyDisplayName(stack, item, baseItem);
        applySkyblockIdTag(stack, item.internalName);
        applyLore(stack, item, baseItem);
        applyItemModel(stack, item);
        applySkullProfile(stack, item);
        applyLeatherColor(stack, item);
        applyEnchantmentGlint(stack, item);

        return stack;
    }

    private static void applyDisplayName(ItemStack stack, NeuItem item, Item baseItem) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(resolveDisplayName(item, baseItem)));
    }

    /**
     * For enchanted books, prefer the first lore line (e.g. {@code "§9Sharpness III"}) over
     * the generic "Enchanted Book" display name so search/compare works on the actual enchant.
     */
    private static String resolveDisplayName(NeuItem item, Item baseItem) {
        if (baseItem != Items.ENCHANTED_BOOK || item.lore == null || item.lore.isEmpty()) {
            return item.displayName;
        }

        String enchantLine = item.lore.getFirst();
        if (enchantLine.isBlank()) return item.displayName;

        String rarityColor = (item.displayName != null && item.displayName.length() >= 2
                && item.displayName.charAt(0) == '§')
                ? item.displayName.substring(0, 2)
                : "§f";
        String enchantText = (enchantLine.length() >= 2 && enchantLine.charAt(0) == '§')
                ? enchantLine.substring(2)
                : enchantLine;
        return rarityColor + enchantText;
    }

    private static void applySkyblockIdTag(ItemStack stack, String internalName) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", internalName);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void applyLore(ItemStack stack, NeuItem item, Item baseItem) {
        if (item.lore == null || item.lore.isEmpty()) return;

        List<String> loreLines = (baseItem == Items.ENCHANTED_BOOK)
                ? item.lore.subList(1, item.lore.size())
                : item.lore;

        List<Component> lines = loreLines.stream().<Component>map(Component::literal).toList();
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    private static void applyItemModel(ItemStack stack, NeuItem item) {
        if (item.itemModel == null) return;
        Identifier modelRl = Identifier.tryParse(item.itemModel);
        if (modelRl != null) stack.set(DataComponents.ITEM_MODEL, modelRl);
    }

    private static void applySkullProfile(ItemStack stack, NeuItem item) {
        if (item.skullTexture == null || item.skullTexture.isEmpty()) return;

        PropertyMap properties = new PropertyMap(
                ImmutableMultimap.of("textures", new Property("textures", item.skullTexture)));
        UUID uuid = UUID.nameUUIDFromBytes(item.skullTexture.getBytes(StandardCharsets.UTF_8));
        GameProfile profile = new GameProfile(uuid, item.internalName, properties);
        stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
    }

    private static void applyLeatherColor(ItemStack stack, NeuItem item) {
        if (item.leatherColor < 0) return;
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(item.leatherColor));
    }

    private static void applyEnchantmentGlint(ItemStack stack, NeuItem item) {
        if (!item.enchantmentGlint) return;
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
    }

    // ── Base-item resolution ────────────────────────────────────────────────────

    private static Item resolveBaseItem(NeuItem item) {
        if (item.snbtItemId != null) {
            Item resolved = lookupItem(item.snbtItemId);
            if (resolved != Items.BARRIER) return resolved;
        }
        return lookupItem(LegacyItemIdMapper.map(item.itemId, item.damage));
    }

    private static Item lookupItem(String id) {
        Identifier rl = Identifier.tryParse(id);
        if (rl == null) return Items.BARRIER;
        Item resolved = BuiltInRegistries.ITEM.getValue(rl);
        return resolved != Items.AIR ? resolved : Items.BARRIER;
    }

    // ── Virtual / fallback stacks ───────────────────────────────────────────────

    private static ItemStack buildCoinStack() {
        ItemStack stack = new ItemStack(Items.GOLD_NUGGET);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("§6Coins"));
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "SKYBLOCK_COIN");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static ItemStack buildUnknownFallback(String neuId, int count) {
        ItemStack fallback = new ItemStack(Items.BARRIER, count);
        fallback.set(DataComponents.CUSTOM_NAME, Component.literal("§c" + neuId));
        CompoundTag tag = new CompoundTag();
        tag.putString("id", neuId);
        fallback.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return fallback;
    }
}