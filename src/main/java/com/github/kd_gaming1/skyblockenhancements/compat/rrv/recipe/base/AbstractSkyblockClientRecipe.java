package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.render.RecipeColors;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.ItemFamilyHelper;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipeUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
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
 * <p>Redirect behaviour defaults to SkyBlock family matching (so clicking a tiered child resolves
 * to the parent's recipe in compact mode). Visual-only views fall back to plain equality and never
 * match as an ingredient.
 *
 * <p>Ingredient/result IDs are pre-computed lazily and stored in a {@link Set} for O(1)
 * {@link #redirectsAsIngredient} / {@link #redirectsAsResult} lookups.
 */
public abstract class AbstractSkyblockClientRecipe implements ReliableClientRecipe {

    protected final String[] wikiUrls;

    private boolean buttonsDirty = true;
    @Nullable private Button sentinelButton;

    /** Lazily-built set of every SkyBlock internal ID present in {@link #getIngredients()}. */
    @Nullable private Set<String> precomputedIngredientIds;
    /** Lazily-built set of every SkyBlock internal ID present in {@link #getResults()}. */
    @Nullable private Set<String> precomputedResultIds;

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

        Set<String> candidates = precomputedResultIds();
        if (candidates.contains(queryId)) return true;
        for (String candidateId : candidates) {
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

    /**
     * Binds a slot with the default optional-slot renderer so a background sprite is drawn
     * behind the item. Skips null or empty content. This should be used for every slot that
     * contains an item so the recipe display looks consistent with native RRV recipe views.
     */
    protected static void bindOptional(RecipeViewMenu.SlotFillContext ctx, int index,
                                       @Nullable SlotContent content) {
        if (content != null && !content.isEmpty()) {
            ctx.bindOptionalSlot(index, content, RecipeViewMenu.OptionalSlotRenderer.DEFAULT);
        }
    }

    // ── Render utilities ────────────────────────────────────────────────────────

    /** Convenience: {@code Minecraft.getInstance().font}. */
    protected final Font font() {
        return Minecraft.getInstance().font;
    }

    private static final Component ARROW_TEXT = Component.literal("→");

    /**
     * Draws the standard "→" arrow used between recipe inputs and outputs.
     * Subclasses call this from {@link #renderRecipe} with their recipe-specific coordinates.
     */
    protected final void renderArrow(GuiGraphics gfx, int x, int y) {
        gfx.drawString(Minecraft.getInstance().font, ARROW_TEXT, x, y, RecipeColors.ARROW, false);
    }

    // ── Internal ─────────────────────────────────────────────────────────────────

    /**
     * Shared logic for {@link #redirectsAsResult} and {@link #redirectsAsIngredient}.
     * Extracts the query stack's ID once, then tests it against a precomputed candidate set.
     */
    private boolean redirectsAgainstPrecomputed(ItemStack stack, Set<String> candidateIds) {
        String queryId = SkyblockRecipeUtil.extractSkyblockId(stack);
        if (queryId == null) return false;

        if (candidateIds.isEmpty()) return false;
        if (candidateIds.contains(queryId)) return true;

        for (String candidateId : candidateIds) {
            if (ItemFamilyHelper.isFamilyMember(queryId, candidateId)) return true;
        }
        return false;
    }

    /**
     * Lazily extracts and caches the SkyBlock internal IDs of all stacks returned by
     * {@link #getIngredients()}. The set is immutable once built.
     */
    private Set<String> precomputedIngredientIds() {
        Set<String> snapshot = precomputedIngredientIds;
        if (snapshot != null) return snapshot;

        precomputedIngredientIds = collectIdsFromSlots(getIngredients());
        return precomputedIngredientIds;
    }

    /**
     * Lazily extracts and caches the SkyBlock internal IDs of all stacks returned by
     * {@link #getResults()}. The set is immutable once built.
     */
    private Set<String> precomputedResultIds() {
        Set<String> snapshot = precomputedResultIds;
        if (snapshot != null) return snapshot;

        precomputedResultIds = collectIdsFromSlots(getResults());
        return precomputedResultIds;
    }

    /** Collects all non-null SkyBlock internal IDs from the given slot contents into a HashSet. */
    private static Set<String> collectIdsFromSlots(List<SlotContent> slots) {
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        for (SlotContent slot : slots) {
            for (ItemStack candidate : slot.getValidContents()) {
                String id = SkyblockRecipeUtil.extractSkyblockId(candidate);
                if (id != null && !id.isEmpty()) {
                    ids.add(id);
                }
            }
        }
        return Collections.unmodifiableSet(ids);
    }
}
