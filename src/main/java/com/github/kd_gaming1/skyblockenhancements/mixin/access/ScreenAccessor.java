package com.github.kd_gaming1.skyblockenhancements.mixin.access;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes {@link Screen#removeWidget} for use outside the screen hierarchy. */
@SuppressWarnings("JavadocReference")
@Mixin(Screen.class)
public interface ScreenAccessor {

    @Invoker("removeWidget")
    void sbe$removeWidget(GuiEventListener listener);

    @Invoker("addWidget")
    <T extends GuiEventListener & net.minecraft.client.gui.narration.NarratableEntry> T sbe$addWidget(T widget);

    @Invoker("addRenderableWidget")
    <T extends GuiEventListener & Renderable & NarratableEntry> T sbe$addRenderableWidget(T widget);
}