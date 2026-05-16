package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.itemlist;

import cc.cassian.rrv.common.overlay.OverlayManager;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.RrvCompat;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.category.SkyblockCategoryFilter;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.category.SkyblockCategoryState;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.injection.FullStackListCache;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Per-stack highlighting for RRV item-filter mode.
 *
 * <p>A slot is dimmed unless it passes <b>all</b> active filters:
 * <ol>
 *   <li>Category button / search-prefix category (if active)</li>
 *   <li>Text query — name + SkyBlock ID, plus optional full-tooltip scan</li>
 * </ol>
 *
 * <p>Tooltip generation is skipped entirely when the config is {@code NAME_AND_ID}.
 * When {@code FULL_TOOLTIP} is active, results are memoised per {@link ItemStack}
 * instance so duplicate slots only pay the cost once per frame.
 */
@Mixin(value = ItemViewOverlay.class, remap = false)
public abstract class RrvItemHighlightMixin {

    @Unique private static final int SLOT_SIZE = 18;
    @Unique private static final int DIM_OVERLAY_COLOR = 0x80000000;
    @Unique private static final char FORMATTING_CHAR = '§';

    @Unique private final IdentityHashMap<ItemStack, String> sbe$nameCache = new IdentityHashMap<>();
    @Unique private final IdentityHashMap<ItemStack, Boolean> sbe$tooltipCache = new IdentityHashMap<>();

    @Inject(method = "renderItemHighlighting", at = @At("HEAD"), cancellable = true, remap = false)
    private void sbe$renderItemHighlighting(
            AbstractContainerScreen<?> screen,
            GuiGraphics guiGraphics,
            int mouseX, int mouseY, float partialTicks,
            CallbackInfo ci) {

        if (!RrvCompat.isActive()) return;

        ItemViewOverlay self = (ItemViewOverlay) (Object) this;
        if (!self.isItemFilterMode()) return;

        String query    = self.getCurrentQuery();
        var category    = SkyblockCategoryState.getActiveCategory();
        String subCat   = SkyblockCategoryState.getActiveSubCategory();

        boolean hasQuery    = query != null && !query.isBlank();
        boolean hasCategory = category != null;

        if (!hasQuery && !hasCategory) return;

        // Pre-lowercase once; all haystack comparisons use String.contains() directly.
        String normalized = hasQuery ? query.toLowerCase(Locale.ROOT).trim() : null;

        Minecraft mc  = Minecraft.getInstance();
        int left      = OverlayManager.INSTANCE.currentInfo().leftPos() - 1;
        int top       = OverlayManager.INSTANCE.currentInfo().topPos() - 1;

        // Clear reused maps rather than allocating new ones each frame.
        sbe$nameCache.clear();
        sbe$tooltipCache.clear();

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(left, top);

        for (Slot slot : screen.getMenu().slots) {
            if (!slot.isActive() || !slot.isHighlightable()) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                guiGraphics.fill(slot.x, slot.y, slot.x + SLOT_SIZE, slot.y + SLOT_SIZE, DIM_OVERLAY_COLOR);
                continue;
            }

            boolean visible = !hasCategory || SkyblockCategoryFilter.matches(stack, category, subCat);
            if (visible && hasQuery && !sbe$matchesSearch(stack, normalized, mc)) {
                visible = false;
            }

            if (!visible) {
                guiGraphics.fill(slot.x, slot.y, slot.x + SLOT_SIZE, slot.y + SLOT_SIZE, DIM_OVERLAY_COLOR);
            }
        }

        guiGraphics.pose().popMatrix();
        ci.cancel();
    }

    // ── Search matching ──────────────────────────────────────────────────────────

    @Unique
    private boolean sbe$matchesSearch(ItemStack stack, String query, Minecraft mc) {
        if (sbe$matchesNameOrId(stack, query)) return true;

        if (SkyblockEnhancementsConfig.rrvSearchMode
                == SkyblockEnhancementsConfig.RrvSearchMode.NAME_AND_ID) return false;

        return sbe$tooltipCache.computeIfAbsent(stack, s -> sbe$tooltipContains(s, query, mc));
    }

    @Unique
    private boolean sbe$matchesNameOrId(ItemStack stack, String query) {
        // computeIfAbsent pays stripFormatting + toLowerCase only once per stack per frame.
        String name = sbe$nameCache.computeIfAbsent(
                stack,
                s -> stripFormatting(s.getHoverName().getString()).toLowerCase(Locale.ROOT));

        if (name.contains(query)) return true;

        String id = FullStackListCache.getCachedId(stack);
        // getCachedId may already return lowercase; if not, lowercase it once here too.
        return id != null && id.toLowerCase(Locale.ROOT).contains(query);
    }

    @Unique
    private boolean sbe$tooltipContains(ItemStack stack, String query, Minecraft mc) {
        List<Component> lines;
        try {
            lines = Screen.getTooltipFromItem(mc, stack);
        } catch (Exception e) {
            return false;
        }
        for (Component line : lines) {
            if (stripFormatting(line.getString()).toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        return false;
    }

    // ── Text utilities ────────────────────────────────────────────────────────────

    @Unique
    private static String stripFormatting(String text) {
        if (text == null || text.indexOf(FORMATTING_CHAR) == -1) {
            return text == null ? "" : text;
        }
        char[] chars = text.toCharArray();
        StringBuilder out = new StringBuilder(chars.length);
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == FORMATTING_CHAR && i + 1 < chars.length) {
                i++;
                continue;
            }
            out.append(chars[i]);
        }
        return out.toString();
    }
}