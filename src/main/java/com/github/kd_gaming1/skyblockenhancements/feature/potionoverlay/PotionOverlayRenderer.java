package com.github.kd_gaming1.skyblockenhancements.feature.potionoverlay;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.gui.context.GuiContext;
import com.github.kd_gaming1.skyblockenhancements.mixin.access.AbstractContainerMenuStateAccessor;
import com.github.kd_gaming1.skyblockenhancements.util.StringUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.lang.ref.WeakReference;

/**
 * Renders configurable tinted overlays on potion effect slots in the Hypixel
 * "Toggle Potion Effects" GUI — green for enabled, red for disabled.
 *
 * <p>Performance strategy:
 * <ul>
 *   <li>Lore parsing happens once per container content change (tracked via {@code stateId}).</li>
 *   <li>Results are stored in two 64-slot bitsets ({@code long}) — one for enabled, one for disabled.</li>
 *   <li>The screen reference is held via {@link WeakReference} to prevent memory leaks.</li>
 *   <li>Per-frame cost is three cheap early-outs + two bit-tests per slot.</li>
 * </ul>
 */
public final class PotionOverlayRenderer {

    private PotionOverlayRenderer() {}

    private static final String TITLE_PREFIX = "Toggle Potion Effects";
    private static final String DISABLED_MARKER = "DISABLED";
    private static final String ENABLED_MARKER = "ENABLED";

    /** Classification result for a single slot. */
    private enum SlotState {
        NONE, DISABLED, ENABLED
    }

    // Cache state — all guarded by single-threaded render thread semantics.
    private static WeakReference<AbstractContainerScreen<?>> cachedScreenRef = new WeakReference<>(null);
    private static int cachedStateId = -1;
    private static long disabledBitset = 0L;
    private static long enabledBitset = 0L;

    /**
     * Called from the mixin at the tail of {@code AbstractContainerScreen.renderSlot}.
     */
    public static void render(GuiGraphics graphics, AbstractContainerScreen<?> screen, Slot slot) {
        if (!SkyblockEnhancementsConfig.enablePotionOverlay) {
            return;
        }

        if (!GuiContext.matches(screen, TITLE_PREFIX)) {
            return;
        }

        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) {
            return;
        }

        int slotIndex = slot.index;
        if (slotIndex < 0 || slotIndex >= 64) {
            // Outside bitset range — evaluate on demand without caching.
            SlotState state = classifySlot(stack);
            if (state != SlotState.NONE) {
                renderOverlay(graphics, slot, state);
            }
            return;
        }

        ensureCacheFresh(screen);

        if ((disabledBitset & (1L << slotIndex)) != 0L) {
            renderOverlay(graphics, slot, SlotState.DISABLED);
        } else if ((enabledBitset & (1L << slotIndex)) != 0L) {
            renderOverlay(graphics, slot, SlotState.ENABLED);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Cache management
    // ═══════════════════════════════════════════════════════════════════════════

    private static void ensureCacheFresh(AbstractContainerScreen<?> screen) {
        AbstractContainerMenu menu = screen.getMenu();
        int stateId = ((AbstractContainerMenuStateAccessor) menu).skyblockenhancements$getStateId();

        AbstractContainerScreen<?> cached = cachedScreenRef.get();
        if (cached == screen && stateId == cachedStateId) {
            return;
        }

        cachedScreenRef = new WeakReference<>(screen);
        cachedStateId = stateId;
        recompute(menu);
    }

    private static void recompute(AbstractContainerMenu menu) {
        long disabled = 0L;
        long enabled = 0L;
        int slotCount = Math.min(menu.slots.size(), 64);

        for (int i = 0; i < slotCount; i++) {
            Slot slot = menu.getSlot(i);
            if (slot.getItem().isEmpty()) {
                continue;
            }

            switch (classifySlot(slot.getItem())) {
                case DISABLED -> disabled |= (1L << i);
                case ENABLED  -> enabled  |= (1L << i);
                default -> { /* no overlay */ }
            }
        }

        disabledBitset = disabled;
        enabledBitset = enabled;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Lore parsing
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Classifies the given item stack as enabled, disabled, or neither,
     * based on its tooltip lore.
     */
    static SlotState classifySlot(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null || lore.lines().isEmpty()) {
            return SlotState.NONE;
        }

        for (Component line : lore.lines()) {
            String text = line.getString();

            // Fast path: check for DISABLED first (it contains 'D').
            if (text.indexOf('D') >= 0
                    && StringUtil.stripColorCodes(text).contains(DISABLED_MARKER)) {
                return SlotState.DISABLED;
            }

            // Fast path: check for ENABLED (it contains 'E').
            if (text.indexOf('E') >= 0
                    && StringUtil.stripColorCodes(text).contains(ENABLED_MARKER)) {
                return SlotState.ENABLED;
            }
        }
        return SlotState.NONE;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Rendering
    // ═══════════════════════════════════════════════════════════════════════════

    private static void renderOverlay(GuiGraphics graphics, Slot slot, SlotState state) {
        int color = switch (state) {
            case DISABLED -> buildColor(SkyblockEnhancementsConfig.disabledPotionOverlayColor);
            case ENABLED  -> buildColor(SkyblockEnhancementsConfig.enabledPotionOverlayColor);
            default -> 0; // unreachable
        };

        int alpha = Math.max(0, Math.min(255, SkyblockEnhancementsConfig.potionOverlayAlpha));
        int argb = (alpha << 24) | (color & 0xFFFFFF);

        int x = slot.x;
        int y = slot.y;
        graphics.fill(x, y, x + 16, y + 16, argb);
    }

    private static int buildColor(String hex) {
        if (hex == null || hex.isEmpty()) {
            return 0xFFFFFF;
        }
        try {
            if (hex.charAt(0) == '#') {
                if (hex.length() == 7) {
                    return Integer.parseInt(hex.substring(1), 16);
                } else if (hex.length() == 9) {
                    // ARGB string — ignore embedded alpha.
                    return Integer.parseInt(hex.substring(3), 16);
                }
            }
        } catch (NumberFormatException ignored) {
        }
        return 0xFFFFFF;
    }
}
