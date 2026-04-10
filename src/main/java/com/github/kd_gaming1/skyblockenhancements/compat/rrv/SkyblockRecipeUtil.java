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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * Shared widget, matching, and NBT logic for all SkyBlock recipe types.
 *
 * <p>{@link #extractSkyblockId} delegates to {@link FullStackListCache#getCachedId} so that
 * overlay-list stacks never pay the {@code NbtComponent.copyTag()} cost in hot paths like
 * {@code redirectsAsResult()} and {@code redirectsAsIngredient()}.
 */
public final class SkyblockRecipeUtil {

    private SkyblockRecipeUtil() {}

    // ── Item matching ────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if {@code query} matches any stack in the given slots by
     * comparing SkyBlock internal IDs.
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
     * Family-aware matching: returns {@code true} if {@code query} matches any stack by
     * exact ID or by being in the same item family (parent ↔ child from {@code parents.json}).
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
     * Extracts the SkyBlock internal ID from a stack.
     *
     * <p>For stacks sourced from the overlay item list this is an O(1) identity-map
     * lookup in {@link FullStackListCache} with no NBT allocation. For all other stacks
     * (recipe ingredients, results, etc.) it falls back to a single {@code copyTag()} call.
     */
    public static String extractSkyblockId(ItemStack stack) {
        return FullStackListCache.getCachedId(stack);
    }

    // ── Tier extraction ─────────────────────────────────────────────────────────

    /**
     * Extracts the trailing numeric tier from an internal ID
     * (e.g. {@code "ACACIA_GENERATOR_11"} → {@code 11}).
     */
    public static int extractTierFromId(String internalId) {
        if (internalId == null || internalId.isEmpty()) return 0;

        int lastUnderscore = internalId.lastIndexOf('_');
        if (lastUnderscore < 0 || lastUnderscore >= internalId.length() - 1) return 0;

        try {
            return Integer.parseInt(internalId.substring(lastUnderscore + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Extracts the tier from the first result slot's internal ID. */
    public static int extractTierFromResults(List<SlotContent> results) {
        if (results == null || results.isEmpty()) return 0;

        for (SlotContent slot : results) {
            for (ItemStack stack : slot.getValidContents()) {
                int tier = extractTierFromId(extractSkyblockId(stack));
                if (tier > 0) return tier;
            }
        }
        return 0;
    }

    // ── Wiki button ──────────────────────────────────────────────────────────────

    /**
     * Adds a "Wiki" button to the recipe view. Prefers the official wiki (index 1)
     * over fandom (index 0) when both are present.
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

    /** Strips null/empty entries from a wiki URL array. */
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
     * result matches {@code targetId}. Resets to page 0 if no match is found.
     */
    public static void seekToMatchingPage(RecipeViewMenu menu, String targetId) {
        if (targetId == null || !menu.hasNextRecipe()) return;

        int maxPage = menu.getMaxPageIndex();

        for (int page = 0; page <= maxPage; page++) {
            List<ReliableClientRecipe> display =
                    ((RecipeViewMenuAccessor) menu).sbe$getCurrentDisplay();

            if (displayContainsResult(display, targetId)) return;

            if (page < maxPage) menu.nextPage();
        }

        // No matching page found — reset to first page
        while (menu.hasPrevRecipe()) {
            menu.prevPage();
        }
    }

    private static boolean displayContainsResult(
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