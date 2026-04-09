package com.github.kd_gaming1.skyblockenhancements.mixin.tooltipscroll;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Provides read access to the {@code text} field of {@link ClientTextTooltip}.
 */
@Mixin(ClientTextTooltip.class)
public interface ClientTextTooltipAccessor {

    @Accessor
    FormattedCharSequence getText();
}