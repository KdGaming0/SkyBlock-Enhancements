package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.render.RecipeColors;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.ItemFamilyHelper;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipeUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Shared scaffolding for SkyBlock recipe client views.
 *
 * <p>Every recipe view needs to (a) add its buttons when rendered, (b) re-add them if RRV's screen
 * rebuilt its widget list, and (c) reset that state on init/fade. This base owns the mechanics of
 * that button lifecycle so subclasses only have to say <em>which</em> buttons to place.
 *
 * <p>Subclasses implement {@link #placeButtons} — called lazily at most once per screen lifecycle.
 * The returned button is kept as a "sentinel": if RRV drops its widgets, the sentinel goes missing
 * from {@code screen.children()} and we re-run placement on the next render.
 *
 * <p>Redirect behaviour defaults to SkyBlock family matching (so clicking a tiered child resolves
 * to the parent's recipe in compact mode). Visual-only views fall back to plain equality and never
 * match as an ingredient.
 */
public abstract class AbstractSkyblockClientRecipe implements ReliableClientRecipe {

    protected final String[] wikiUrls;

    private boolean buttonsDirty = true;
    @Nullable private Button sentinelButton;

    /** Lazily-built list of every SkyBlock internal ID present in {@link #getIngredients()}. */
    @Nullable private List<String> precomputedIngredientIds;
    /** Lazily-built list of every SkyBlock internal ID present in {@link #getResults()}. */
    @Nullable private List<String> precomputedResultIds;

    protected AbstractSkyblockClientRecipe(String[] wikiUrls) {
        this.wikiUrls = SkyblockRecipeUtil.sanitizeWikiUrls(wikiUrls);
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────────

    @Override
    public void initRecipe() {
        buttonsDirty = true;
    }

    @Override
    public void fadeRecipe() {
        buttonsDirty = true;
        sentinelButton = null;
    }

    // ── Redirect defaults ────────────────────────────────────────────────────────

    @Override
    public boolean redirectsAsResult(ItemStack stack) {
        if (isVisualOnly()) {
            return redirectsAgainstPrecomputed(stack, precomputedResultIds());
        }
        String queryId = SkyblockRecipeUtil.extractSkyblockId(stack);
        if (queryId == null) return false;

        for (String candidateId : precomputedResultIds()) {
            if (queryId.equals(candidateId)) return true;
            if (ItemFamilyHelper.isFamilyMember(queryId, candidateId)) return true;
        }
        return false;
    }

    @Override
    public boolean redirectsAsIngredient(ItemStack stack) {
        if (isVisualOnly()) return false;
        return redirectsAgainstPrecomputed(stack, precomputedIngredientIds());
    }

    // ── Button lifecycle ─────────────────────────────────────────────────────────

    /**
     * Call from {@link #renderRecipe} after custom drawing. Places this recipe's buttons
     * if they're dirty, or re-places them if the screen has dropped its widgets.
     */
    protected final void maintainButtons(RecipeViewScreen screen, RecipePosition pos) {
        boolean screenDropped = sentinelButton != null && !screen.children().contains(sentinelButton);
        if (!buttonsDirty && !screenDropped) return;

        sentinelButton = placeButtons(screen, pos);
        buttonsDirty = false;
    }

    /**
     * Place all recipe-specific buttons on the screen. Return a "sentinel" — any one of the
     * placed buttons — used to detect when the screen's widget list has been cleared.
     * Return {@code null} when no buttons were placed.
     */
    @Nullable
    protected abstract Button placeButtons(RecipeViewScreen screen, RecipePosition pos);

    /**
     * Convenience shortcut for the common case of a single wiki button. Returns the added
     * button (or {@code null} if no wiki URLs were supplied) so it can be used as the sentinel.
     */
    @Nullable
    protected final Button placeWikiButton(RecipeViewScreen screen, int x, int y) {
        return SkyblockRecipeUtil.addWikiButton(screen, wikiUrls, x, y);
    }

    // ── Render utilities ────────────────────────────────────────────────────────

    /** Convenience: {@code Minecraft.getInstance().font}. */
    protected final Font font() {
        return Minecraft.getInstance().font;
    }

    /**
     * Draws the standard "→" arrow used between recipe inputs and outputs.
     * Subclasses call this from {@link #renderRecipe} with their recipe-specific coordinates.
     */
    protected final void renderArrow(GuiGraphics gfx, int x, int y) {
        gfx.drawString(Minecraft.getInstance().font, Component.literal("→"), x, y, RecipeColors.ARROW, false);
    }

    // ── Internal ─────────────────────────────────────────────────────────────────

    /**
     * Shared logic for {@link #redirectsAsResult} and {@link #redirectsAsIngredient}.
     * Extracts the query stack's ID once, then tests it against a precomputed candidate list.
     */
    private boolean redirectsAgainstPrecomputed(ItemStack stack, List<String> candidateIds) {
        String queryId = SkyblockRecipeUtil.extractSkyblockId(stack);
        if (queryId == null) return false;

        if (candidateIds.isEmpty()) return false;

        for (String candidateId : candidateIds) {
            if (queryId.equals(candidateId)) return true;
            if (ItemFamilyHelper.isFamilyMember(queryId, candidateId)) return true;
        }
        return false;
    }

    /**
     * Lazily extracts and caches the SkyBlock internal IDs of all stacks returned by
     * {@link #getIngredients()}. The list is immutable once built.
     */
    private List<String> precomputedIngredientIds() {
        List<String> snapshot = precomputedIngredientIds;
        if (snapshot != null) return snapshot;

        precomputedIngredientIds = collectIdsFromSlots(getIngredients());
        return precomputedIngredientIds;
    }

    /**
     * Lazily extracts and caches the SkyBlock internal IDs of all stacks returned by
     * {@link #getResults()}. The list is immutable once built.
     */
    private List<String> precomputedResultIds() {
        List<String> snapshot = precomputedResultIds;
        if (snapshot != null) return snapshot;

        precomputedResultIds = collectIdsFromSlots(getResults());
        return precomputedResultIds;
    }

    /** Collects all non-null SkyBlock internal IDs from the given slot contents. */
    private static List<String> collectIdsFromSlots(List<SlotContent> slots) {
        List<String> ids = new ArrayList<>();
        for (SlotContent slot : slots) {
            for (ItemStack candidate : slot.getValidContents()) {
                String id = SkyblockRecipeUtil.extractSkyblockId(candidate);
                if (id != null && !id.isEmpty()) {
                    ids.add(id);
                }
            }
        }
        return Collections.unmodifiableList(ids);
    }
}
