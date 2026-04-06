package com.github.kd_gaming1.skyblockenhancements.compat.rrv;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Shared widget, matching, and NBT logic for all SkyBlock recipe types. */
public final class SkyblockRecipeUtil {

    private SkyblockRecipeUtil() {}

    // ── Item matching ────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if {@code query} matches any stack in the given slots by comparing
     * the Skyblock internal ID from {@code custom_data.id}.
     */
    public static boolean matchesAny(ItemStack query, List<SlotContent> slots) {
        String queryId = extractSkyblockId(query);
        if (queryId == null) return false;

        for (SlotContent slot : slots) {
            for (ItemStack candidate : slot.getValidContents()) {
                if (queryId.equals(extractSkyblockId(candidate))) return true;
            }
        }
        return false;
    }

    /**
     * Extracts the Skyblock internal ID (e.g. {@code "ASPECT_OF_THE_END"}) from a stack's
     * {@code custom_data.id} field. Returns {@code null} if not present.
     */
    public static String extractSkyblockId(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        Optional<String> id = customData.copyTag().getString("id");
        return id.orElse(null);
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

        for (GuiEventListener exp : expected) {
            boolean found = false;
            for (GuiEventListener child : children) {
                if (child == exp) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }
}