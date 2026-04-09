package com.github.kd_gaming1.skyblockenhancements.feature.tooltipscroll;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.jspecify.annotations.NonNull;

/**
 * A tooltip positioner that delegates to vanilla for tooltips that fit on screen,
 * but anchors the top of the tooltip near the screen top when the tooltip is too
 * tall to fit in its vanilla position.
 */
public final class TopAnchoredTooltipPositioner implements ClientTooltipPositioner {

    public static final TopAnchoredTooltipPositioner INSTANCE =
            new TopAnchoredTooltipPositioner();

    /** Small gap so the tooltip doesn't touch the very top edge of the screen. */
    private static final int TOP_PADDING = 4;

    private TopAnchoredTooltipPositioner() {}

    @Override
    public @NonNull Vector2ic positionTooltip(
            int screenWidth,
            int screenHeight,
            int mouseX,
            int mouseY,
            int tooltipWidth,
            int tooltipHeight) {

        // Let vanilla decide where it wants to place the tooltip.
        Vector2ic vanilla = DefaultTooltipPositioner.INSTANCE.positionTooltip(
                screenWidth, screenHeight, mouseX, mouseY, tooltipWidth, tooltipHeight);

        if (vanilla.y() < TOP_PADDING) {
            return new Vector2i(vanilla.x(), TOP_PADDING);
        }

        return vanilla;
    }
}