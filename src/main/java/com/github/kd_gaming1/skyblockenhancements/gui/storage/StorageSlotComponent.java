package com.github.kd_gaming1.skyblockenhancements.gui.storage;

import com.daqem.uilib.gui.component.AbstractComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * Renders a single storage slot.
 *
 * <p>Empty slots draw a dark background rectangle. Non-empty slots render the
 * item using {@link GuiGraphics#renderItem}.
 */
public class StorageSlotComponent extends AbstractComponent {

    private final ItemStack stack;
    private final int slotIndex;

    public StorageSlotComponent(int x, int y, int size, ItemStack stack, int slotIndex) {
        super(x, y, size, size);
        this.stack = stack != null ? stack : ItemStack.EMPTY;
        this.slotIndex = slotIndex;
    }

    public ItemStack getStack() {
        return stack;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public boolean isHovered(int mouseX, int mouseY) {
        int tx = getTotalX();
        int ty = getTotalY();
        return mouseX >= tx && mouseX < tx + getWidth()
                && mouseY >= ty && mouseY < ty + getHeight();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt, int pw, int ph) {
        int x = getTotalX();
        int y = getTotalY();
        int size = getWidth();

        // Background
        g.fill(x, y, x + size, y + size, stack.isEmpty() ? StorageColors.SLOT_BG_EMPTY : 0xFF3A3A4E);

        if (stack.isEmpty()) return;

        // Items are natively 16×16; center if slot is larger, or let overflow if smaller
        int offset = (size - 16) / 2;
        g.renderItem(stack, x + offset, y + offset);

        if (size >= 16) {
            g.renderItemDecorations(Minecraft.getInstance().font, stack, x + offset, y + offset);
        }
    }
}
