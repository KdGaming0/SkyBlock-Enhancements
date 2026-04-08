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

    private static final int TOP_PADDING = 4;
    private static final int TOOLTIP_VERTICAL_SURROUND = (9 + 3) * 2; // = 24

    private TopAnchoredTooltipPositioner() {}

    @Override
    public @NonNull Vector2ic positionTooltip(
            int screenWidth,
            int screenHeight,
            int mouseX,
            int mouseY,
            int tooltipWidth,
            int tooltipHeight) {

        Vector2ic vanilla = DefaultTooltipPositioner.INSTANCE.positionTooltip(
                screenWidth, screenHeight, mouseX, mouseY, tooltipWidth, tooltipHeight);

        int renderedHeight = tooltipHeight + TOOLTIP_VERTICAL_SURROUND;
        if (vanilla.y() + renderedHeight <= screenHeight) {
            return vanilla;
        }

        return new Vector2i(vanilla.x(), TOP_PADDING);
    }
}