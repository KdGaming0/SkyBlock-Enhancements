package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.itemlist;

import cc.cassian.rrv.common.overlay.OverlayManager;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.RrvCompat;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.category.SkyblockCategoryState;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.search.InventorySearchEvaluator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Per-stack inventory highlighting for RRV item-filter mode.
 *
 * <p>Replaces the original implementation which built tooltips on every frame
 * ({@code Screen.getTooltipFromItem}) with a zero-allocation evaluator that queries
 * precomputed {@link com.github.kd_gaming1.skyblockenhancements.compat.rrv.search.SkyblockSearchIndex}
 * token sets. This eliminates:
 * <ul>
 *   <li>Per-frame {@code IdentityHashMap} allocation & clearing</li>
 *   <li>Tooltip callback storms that break stateful mods (e.g. compactor)</li>
 *   <li>Repeated {@code StringBuilder} / formatting strip work</li>
 * </ul>
 */
@Mixin(value = ItemViewOverlay.class, remap = false)
public abstract class RrvItemHighlightMixin {

    @Unique private static final int SLOT_SIZE = 18;
    @Unique private static final int DIM_OVERLAY_COLOR = 0x80000000;

    @Inject(method = "renderItemHighlighting", at = @At("HEAD"), cancellable = true, remap = false)
    private void sbe$renderItemHighlighting(
            AbstractContainerScreen<?> screen,
            GuiGraphics guiGraphics,
            int mouseX, int mouseY, float partialTicks,
            CallbackInfo ci) {

        if (!RrvCompat.isActive()) {
            return;
        }

        ItemViewOverlay self = (ItemViewOverlay) (Object) this;
        if (!self.isItemFilterMode()) {
            return;
        }

        String query = self.getCurrentQuery();
        var category = SkyblockCategoryState.getActiveCategory();
        String subCat = SkyblockCategoryState.getActiveSubCategory();

        boolean hasFilter = (query != null && !query.isBlank()) || category != null;
        if (!hasFilter) {
            return;
        }

        int left = OverlayManager.INSTANCE.currentInfo().leftPos() - 1;
        int top = OverlayManager.INSTANCE.currentInfo().topPos() - 1;

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(left, top);

        for (Slot slot : screen.getMenu().slots) {
            if (!slot.isActive() || !slot.isHighlightable()) {
                continue;
            }

            if (slot.getItem().isEmpty()) {
                guiGraphics.fill(slot.x, slot.y,
                        slot.x + SLOT_SIZE, slot.y + SLOT_SIZE,
                        DIM_OVERLAY_COLOR);
                continue;
            }

            if (!InventorySearchEvaluator.matches(slot.getItem(), query, category, subCat)) {
                guiGraphics.fill(slot.x, slot.y,
                        slot.x + SLOT_SIZE, slot.y + SLOT_SIZE,
                        DIM_OVERLAY_COLOR);
            }
        }

        guiGraphics.pose().popMatrix();
        ci.cancel();
    }
}