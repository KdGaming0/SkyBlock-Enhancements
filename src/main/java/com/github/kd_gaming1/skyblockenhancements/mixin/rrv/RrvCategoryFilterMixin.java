package com.github.kd_gaming1.skyblockenhancements.mixin.rrv;

import cc.cassian.rrv.common.overlay.AbstractRrvOverlay.ScreenContext;
import cc.cassian.rrv.common.overlay.itemlist.AbstractRrvItemListOverlay;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import cc.cassian.rrv.common.overlay.itemlist.view.SearchBar;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.RrvCompat;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SkyblockCategoryButtons;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SkyblockCategoryFilter;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SkyblockCategoryState;
import com.github.kd_gaming1.skyblockenhancements.repo.SkyblockItemCategory;
import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds category filtering and toggle buttons to the RRV item list overlay.
 *
 * <p>Active category state lives in {@link SkyblockCategoryState} — no search-bar text is read
 * or written. Two injection points:
 *
 * <ol>
 *   <li>{@code updateQuery} TAIL — applies the category filter after RRV finishes its own query.
 *   <li>{@code placeWidgets} TAIL — adds the toggle buttons above the search bar.
 * </ol>
 */
@Mixin(ItemViewOverlay.class)
public abstract class RrvCategoryFilterMixin {

    @Shadow(remap = false)
    private SearchBar searchbar;

    // ── Category filter ─────────────────────────────────────────────────────────

    /**
     * Narrows the item list to the active category after RRV has finished applying its own query
     * filters. No-ops when no category is selected.
     */
    @Inject(method = "updateQuery", at = @At("TAIL"), remap = false)
    private void sbe$applyCategoryFilter(String newQuery, CallbackInfo ci) {
        if (!RrvCompat.isActive()) return;

        SkyblockItemCategory target = SkyblockCategoryState.getActiveCategory();
        if (target == null) return;

        AbstractRrvItemListOverlay self = (AbstractRrvItemListOverlay) (Object) this;
        self.availableItems().removeIf(stack -> !SkyblockCategoryFilter.matches(stack, target));
        self.updateSlots();
    }

    // ── Button injection ────────────────────────────────────────────────────────

    /** Adds category toggle buttons above the search bar after RRV places its own widgets. */
    @Inject(method = "placeWidgets", at = @At("TAIL"), remap = false)
    private void sbe$addCategoryButtons(ScreenContext ctx, CallbackInfo ci) {
        if (!RrvCompat.isActive() || searchbar == null) return;

        for (Button btn :
                SkyblockCategoryButtons.create(
                        searchbar.getX(), searchbar.getY(), searchbar.getWidth())) {
            ctx.addRenderable(btn);
        }
    }
}