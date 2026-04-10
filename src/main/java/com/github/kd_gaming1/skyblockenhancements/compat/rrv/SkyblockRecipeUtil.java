package com.github.kd_gaming1.skyblockenhancements.compat.rrv;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import com.github.kd_gaming1.skyblockenhancements.mixin.rrv.RecipeViewMenuAccessor;
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
     * Family-aware matching: returns {@code true} if {@code query} matches any stack in
     * the given slots either by exact ID match OR by being in the same item family (parent
     * ↔ child relationship from {@code parents.json}). Only checks family in compact mode.
     *
     * <p>This enables clicking a parent item (e.g. "Iron Minion I") to show recipes for
     * all child tiers (Iron Minion II–XI) in the recipe view.
     */
    public static boolean matchesAnyOrFamily(ItemStack query, List<SlotContent> slots) {
        String queryId = extractSkyblockId(query);
        if (queryId == null) return false;

        for (SlotContent slot : slots) {
            for (ItemStack candidate : slot.getValidContents()) {
                String candidateId = extractSkyblockId(candidate);
                if (candidateId == null) continue;

                if (queryId.equals(candidateId)) return true;

                if (ItemFamilyHelper.isFamilyMember(queryId, candidateId)) return true;
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

    // ── Tier extraction ─────────────────────────────────────────────────────────

    /**
     * Extracts the trailing numeric tier from an internal ID (e.g.
     * {@code "ACACIA_GENERATOR_11"} → {@code 11}, {@code "DIAMOND_SWORD"} → {@code 0}).
     * Returns 0 if no trailing number is found.
     */
    public static int extractTierFromId(String internalId) {
        if (internalId == null || internalId.isEmpty()) return 0;

        int lastUnderscore = internalId.lastIndexOf('_');
        if (lastUnderscore < 0 || lastUnderscore >= internalId.length() - 1) return 0;

        String suffix = internalId.substring(lastUnderscore + 1);
        try {
            return Integer.parseInt(suffix);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Extracts the tier from the first result slot's internal ID. Used by client recipes
     * to compute tier-aware priority for ordered display.
     *
     * @param results the recipe's result slots
     * @return the tier number, or 0 if not determinable
     */
    public static int extractTierFromResults(List<SlotContent> results) {
        if (results == null || results.isEmpty()) return 0;

        for (SlotContent slot : results) {
            for (ItemStack stack : slot.getValidContents()) {
                String id = extractSkyblockId(stack);
                int tier = extractTierFromId(id);
                if (tier > 0) return tier;
            }
        }
        return 0;
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
    public static void openUri(String uri) {
        try {
            Util.getPlatform().openUri(URI.create(uri));
        } catch (Exception ignored) {
        }
    }

    // ── NBT helpers for wiki URLs ────────────────────────────────────────────────

    /** Writes wiki URLs to an NBT tag for server→client transport. */
    public static void writeWikiUrls(CompoundTag tag, String[] urls) {
        if (urls == null || urls.length == 0) return;
        ListTag list = new ListTag();
        for (String url : urls) {
            list.add(StringTag.valueOf(url));
        }
        tag.put("wiki", list);
    }

    /** Reads wiki URLs from an NBT tag. Returns an empty array if not present. */
    public static String[] readWikiUrls(CompoundTag tag) {
        ListTag list = tag.getListOrEmpty("wiki");
        if (list.isEmpty()) return new String[0];
        String[] urls = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            urls[i] = String.valueOf(list.getString(i));
        }
        return urls;
    }

    /** Strips null entries from a wiki URL array. */
    public static String[] sanitizeWikiUrls(String[] urls) {
        if (urls == null) return new String[0];
        int validCount = 0;
        for (String url : urls) {
            if (url != null && !url.isEmpty()) validCount++;
        }
        if (validCount == urls.length) return urls;
        String[] clean = new String[validCount];
        int idx = 0;
        for (String url : urls) {
            if (url != null && !url.isEmpty()) clean[idx++] = url;
        }
        return clean;
    }

    /**
     * Identity-based contains check for widget lists. Used to detect whether our buttons
     * are still in the screen's children list (screens clear children on resize).
     */
    public static boolean containsAllByIdentity(
            List<? extends GuiEventListener> children, List<? extends GuiEventListener> targets) {
        for (GuiEventListener target : targets) {
            boolean found = false;
            for (GuiEventListener child : children) {
                if (child == target) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    /**
     * Advances {@code menu} forward until its current display contains a recipe whose
     * result matches {@code targetId} by exact Skyblock internal ID. If no page matches,
     * resets to page 0 so behavior is unchanged from before.
     *
     * <p>Only meaningful for multi-page menus (family items). Single-page menus are
     * no-ops because {@link RecipeViewMenu#hasNextRecipe()} returns false.
     */
    public static void seekToMatchingPage(RecipeViewMenu menu, String targetId) {
        if (targetId == null || !menu.hasNextRecipe()) return;

        int maxPage = menu.getMaxPageIndex();

        for (int page = 0; page <= maxPage; page++) {
            List<ReliableClientRecipe> display =
                    ((RecipeViewMenuAccessor) menu).sbe$getCurrentDisplay();

            if (sbe$displayContainsResult(display, targetId)) return;

            if (page < maxPage) menu.nextPage();
        }

        while (menu.hasPrevRecipe()) {
            menu.prevPage();
        }
    }

    private static boolean sbe$displayContainsResult(
            List<ReliableClientRecipe> display, String targetId) {
        for (ReliableClientRecipe recipe : display) {
            for (SlotContent result : recipe.getResults()) {
                for (ItemStack candidate : result.getValidContents()) {
                    if (targetId.equals(extractSkyblockId(candidate))) return true;
                }
            }
        }
        return false;
    }
}