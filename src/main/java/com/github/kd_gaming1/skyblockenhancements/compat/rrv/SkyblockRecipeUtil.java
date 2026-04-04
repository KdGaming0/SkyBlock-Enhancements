package com.github.kd_gaming1.skyblockenhancements.compat.rrv;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import java.net.URI;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/** Shared widget, matching, and NBT logic for all SkyBlock recipe types. */
public final class SkyblockRecipeUtil {

    private SkyblockRecipeUtil() {}

    // ── Item matching ────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if {@code query} matches any stack in the given slots.
     *
     * <p>SkyBlock items share vanilla item types (e.g. all custom swords are {@code diamond_sword}),
     * so we compare by {@code CUSTOM_NAME} first — that's unique per SkyBlock item. The
     * {@code ITEM_MODEL} fallback covers vanilla items that don't have custom names.
     */
    public static boolean matchesAny(ItemStack query, List<SlotContent> slots) {
        var qName = query.get(DataComponents.CUSTOM_NAME);
        Identifier qModel = query.get(DataComponents.ITEM_MODEL);

        for (SlotContent slot : slots) {
            for (ItemStack candidate : slot.getValidContents()) {
                var cName = candidate.get(DataComponents.CUSTOM_NAME);

                if (qName != null && cName != null) {
                    if (qName.equals(cName)) return true;
                    continue;
                }

                if (qName == null && cName == null && qModel != null) {
                    if (qModel.equals(candidate.get(DataComponents.ITEM_MODEL))) return true;
                }
            }
        }
        return false;
    }

    // ── Wiki button ──────────────────────────────────────────────────────────────

    /**
     * Adds a "Wiki" button to the recipe view. Prefers the official wiki (index 1) over
     * fandom (index 0) when both are present.
     *
     * @return the created button, or {@code null} if {@code wikiUrls} is empty
     */
    public static Button addWikiButton(
            RecipeViewScreen screen, String[] wikiUrls, int btnX, int btnY) {
        if (wikiUrls == null || wikiUrls.length == 0) return null;

        String url = wikiUrls.length > 1 ? wikiUrls[1] : wikiUrls[0];
        Button btn = Button.builder(Component.literal("Wiki"), b -> openUri(url))
                .pos(btnX, btnY)
                .size(56, 12)
                .build();
        screen.addRecipeWidget(btn);
        return btn;
    }

    /** Opens a URI in the system browser, silently ignoring failures. */
    public static void openUri(String url) {
        try {
            Util.getPlatform().openUri(URI.create(url));
        } catch (Exception ignored) {
        }
    }

    // ── Wiki URL NBT (shared across all server recipe types) ─────────────────────

    /** Writes a {@code String[]} of wiki URLs into a CompoundTag under key {@code "wikiUrls"}. */
    public static void writeWikiUrls(CompoundTag tag, String[] wikiUrls) {
        ListTag urls = new ListTag();
        for (String url : wikiUrls) {
            urls.add(StringTag.valueOf(url != null ? url : ""));
        }
        tag.put("wikiUrls", urls);
    }

    /** Reads a {@code String[]} of wiki URLs from a CompoundTag under key {@code "wikiUrls"}. */
    public static String[] readWikiUrls(CompoundTag tag) {
        ListTag urlsTag = tag.getListOrEmpty("wikiUrls");
        String[] urls = new String[urlsTag.size()];
        for (int i = 0; i < urlsTag.size(); i++) {
            urls[i] = urlsTag.get(i).asString().orElse("");
        }
        return urls;
    }

    /** Ensures a non-null wiki URL array, defaulting to empty. */
    public static String[] sanitizeWikiUrls(String[] wikiUrls) {
        return wikiUrls != null ? wikiUrls : new String[0];
    }

    /**
     * Returns true if every element in {@code expected} is present in {@code children},
     * comparing by object identity (==), which matches widget instance tracking.
     */
    public static boolean containsAllByIdentity(
            List<? extends GuiEventListener> children,
            List<? extends GuiEventListener> expected) {
        if (expected.isEmpty()) return true;

        Set<GuiEventListener> remaining =
                Collections.newSetFromMap(new IdentityHashMap<>());
        remaining.addAll(expected);

        for (GuiEventListener child : children) {
            remaining.remove(child);
            if (remaining.isEmpty()) return true;
        }
        return false;
    }
}