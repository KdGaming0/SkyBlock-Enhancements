package com.github.kd_gaming1.skyblockenhancements.feature.slotmanage;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;
import java.util.Map;

/**
 * Renders the slot-management overlay: the padlock on locked slots, the coloured outlines on bound
 * source/target slots, the pulsing pending-source highlight, and the connecting lines.
 *
 * <p>Outlines are drawn per-slot (so they can sit below the stack-count text); lines are drawn once
 * per frame across the whole inventory. Colours come from config; the pending highlight is a fixed
 * pulsing cyan to stay distinct from the bound colours.
 */
public final class SlotOverlayRenderer {

    private SlotOverlayRenderer() {}

    private static final Identifier LOCK_SPRITE =
            Identifier.fromNamespaceAndPath("skyblock_enhancements", "slot/locked");

    private static final int OUTLINE_ALPHA = 0xCC000000;
    private static final int LINE_ALPHA = 0xFF000000;
    private static final int PENDING_RGB = 0x00FFFF; // cyan
    private static final int FALLBACK_RGB = 0xFFFF00; // yellow, used if a config colour is malformed

    /** Per-slot padlock on locked player-inventory slots. */
    public static void renderLock(GuiGraphicsExtractor graphics, Slot slot) {
        if (!SkyblockEnhancementsConfig.enableSlotLocking) return;
        if (!(slot.container instanceof Inventory)) return;
        if (!SlotManager.isLocked(slot.getContainerSlot())) return;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LOCK_SPRITE, slot.x, slot.y, 16, 16);
    }

    /** Per-slot bind outline (source/target/pending). Drawn below the stack-count text. */
    public static void renderBindOutline(GuiGraphicsExtractor graphics, Slot slot) {
        if (!SkyblockEnhancementsConfig.enableSlotBinding) return;
        if (!(slot.container instanceof Inventory)) return;

        int containerSlot = slot.getContainerSlot();
        Integer pending = SlotManager.getPendingBindSlot();
        if (SlotManager.isEditKeyDown() && pending != null && pending == containerSlot) {
            graphics.outline(slot.x, slot.y, 16, 16, (pulseAlpha() << 24) | PENDING_RGB);
            return;
        }

        if (!boundOutlinesVisible()) return;
        Map<Integer, Integer> binds = SlotManager.getCurrentBindings();
        if (binds.containsKey(containerSlot)) {
            graphics.outline(slot.x, slot.y, 16, 16, outlineColor(SkyblockEnhancementsConfig.slotBindSourceColor));
        } else if (containerSlot >= 0 && containerSlot <= 8 && binds.containsValue(containerSlot)) {
            graphics.outline(slot.x, slot.y, 16, 16, outlineColor(SkyblockEnhancementsConfig.slotBindTargetColor));
        }
    }

    /**
     * Whole-inventory connecting lines, only while the Slot Edit key or Shift is held. Called from
     * {@code extractContents} TAIL, where the pose matrix is back in screen space, so we re-translate
     * to the slot coordinate origin.
     */
    public static void renderLines(GuiGraphicsExtractor graphics, int leftPos, int topPos, List<Slot> slots) {
        if (!SkyblockEnhancementsConfig.enableSlotBinding) return;
        if (!(SlotManager.isEditKeyDown() || SlotManager.isShiftDown())) return;

        Map<Integer, Integer> binds = SlotManager.getCurrentBindings();
        if (binds.isEmpty()) return;

        int lineColor = LINE_ALPHA | rgb(SkyblockEnhancementsConfig.slotBindLineColor);
        graphics.pose().pushMatrix();
        graphics.pose().translate(leftPos, topPos);
        for (Map.Entry<Integer, Integer> entry : binds.entrySet()) {
            Slot source = findInventorySlot(slots, entry.getKey());
            Slot target = findHotbarSlot(slots, entry.getValue());
            if (source != null && target != null) {
                SlotLineRenderer.drawLine(graphics,
                        source.x + 8, source.y + 8, target.x + 8, target.y + 8, lineColor);
            }
        }
        graphics.pose().popMatrix();
    }

    private static boolean boundOutlinesVisible() {
        return switch (SkyblockEnhancementsConfig.slotBindOutlineVisibility) {
            case ALWAYS -> true;
            case ACTIVE_ONLY -> SlotManager.isEditKeyDown() || SlotManager.isShiftDown();
            case HIDDEN -> false;
        };
    }

    private static Slot findInventorySlot(List<Slot> slots, int containerSlot) {
        for (Slot slot : slots) {
            if (slot.container instanceof Inventory && slot.getContainerSlot() == containerSlot) {
                return slot;
            }
        }
        return null;
    }

    private static Slot findHotbarSlot(List<Slot> slots, int hotbarButton) {
        if (hotbarButton < 0 || hotbarButton > 8) return null;
        return findInventorySlot(slots, hotbarButton);
    }

    private static int outlineColor(String hex) {
        return OUTLINE_ALPHA | rgb(hex);
    }

    private static int rgb(String hex) {
        if (hex != null && hex.length() == 7 && hex.charAt(0) == '#') {
            try {
                return Integer.parseInt(hex.substring(1), 16);
            } catch (NumberFormatException ignored) {
            }
        }
        return FALLBACK_RGB;
    }

    private static int pulseAlpha() {
        return 128 + (int) (96 * Math.sin(Util.getMillis() / 200.0));
    }
}
