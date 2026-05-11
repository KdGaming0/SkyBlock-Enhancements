package com.github.kd_gaming1.skyblockenhancements.gui.storage;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;

/**
 * Delegate attached to a vanilla {@link net.minecraft.client.gui.screens.inventory.AbstractContainerScreen}
 * to overlay custom storage rendering and input handling.
 *
 * <p>The mixin calls into this delegate at key moments instead of drawing the
 * vanilla chest foreground.
 */
public abstract class ContainerOverlay {

    /** Called once when the screen initializes (and on resize). */
    public abstract void onInit(int screenWidth, int screenHeight);

    /** Called at the start of render() before vanilla evaluates hoveredSlot. */
    public void preRender(int mouseX, int mouseY) {}

    /** Called from render() instead of drawing the vanilla foreground. */
    public abstract void render(GuiGraphics graphics, float delta, int mouseX, int mouseY);

    /** Return false to suppress the default foreground (slot backgrounds, etc.). */
    public boolean shouldDrawForeground() {
        return false;
    }

    /** Restrict slot hit-testing. Return false to disable interaction for a slot. */
    public boolean isPointOverSlot(Slot slot, double pointX, double pointY) {
        return true;
    }

    /** Input delegation. Return true if consumed. */
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return false;
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        return false;
    }

    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return false;
    }

    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        return false;
    }

    /** Return the search field widget so the mixin can register it with the screen. */
    public net.minecraft.client.gui.components.EditBox getSearchField() {
        return null;
    }

    /** REI/JEI exclusion zones. Return rectangles the overlay occupies. */
    public abstract List<Rect> getBounds();
}
