package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipeUtil;
import net.minecraft.client.gui.components.Button;
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

    protected AbstractSkyblockClientRecipe(String[] wikiUrls) {
        this.wikiUrls = SkyblockRecipeUtil.sanitizeWikiUrls(wikiUrls);
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────────

    @Override
    public final void initRecipe() {
        buttonsDirty = true;
    }

    @Override
    public final void fadeRecipe() {
        buttonsDirty = true;
        sentinelButton = null;
    }

    // ── Redirect defaults ────────────────────────────────────────────────────────

    @Override
    public boolean redirectsAsResult(ItemStack stack) {
        return isVisualOnly()
                ? SkyblockRecipeUtil.matchesAny(stack, getResults())
                : SkyblockRecipeUtil.matchesAnyOrFamily(stack, getResults());
    }

    @Override
    public boolean redirectsAsIngredient(ItemStack stack) {
        if (isVisualOnly()) return false;
        return SkyblockRecipeUtil.matchesAnyOrFamily(stack, getIngredients());
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
}