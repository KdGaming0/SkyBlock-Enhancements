package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.wiki;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.AbstractSkyblockClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipePriority;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class SkyblockWikiInfoClientRecipe extends AbstractSkyblockClientRecipe
        implements ReliableClientRecipe {
    
    private static final int NAME_X          = 22;
    private static final int NAME_Y          = 4;
    private static final int LINE_HEIGHT     = 10;
    private static final int LINE_GAP        = 1;
    private static final int MAX_NAME_LINES  = 2;
    private static final int RIGHT_PADDING   = 4;
    private static final int BUTTON_Y_OFFSET = 20;
    private static final int TEXT_COLOR      = 0xFF404040;
    private static final String ELLIPSIS     = "…";

    private final @Nullable SlotContent displayItem;
    private final Component itemName;

    public SkyblockWikiInfoClientRecipe(ItemStack displayItem, String displayName, String[] wikiUrls) {
        super(wikiUrls);
        boolean valid = displayItem != null && !displayItem.isEmpty();
        this.displayItem = valid ? SlotContent.of(displayItem) : null;
        this.itemName    = resolveName(displayItem, displayName, valid);
    }

    /** Prefer the explicit displayName; fall back to the stack's hover name, then empty. */
    private static Component resolveName(@Nullable ItemStack stack, @Nullable String displayName, boolean valid) {
        if (displayName != null && !displayName.isBlank()) {
            return Component.literal(displayName);
        }
        if (valid) {
            assert stack != null;
            return stack.getHoverName();
        }
        return Component.empty();
    }

    @Override
    public ReliableClientRecipeType getViewType() {
        return SkyblockWikiInfoRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        if (displayItem != null) ctx.bindSlot(0, displayItem);
    }

    @Override
    public List<SlotContent> getIngredients() {
        return List.of();
    }

    @Override
    public List<SlotContent> getResults() {
        return displayItem != null ? List.of(displayItem) : List.of();
    }

    @Override
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos, GuiGraphics gfx,
                             int mouseX, int mouseY, float partialTicks) {
        Font font = Minecraft.getInstance().font;
        int wrapWidth = getViewType().getDisplayWidth() - NAME_X - RIGHT_PADDING;
        List<FormattedCharSequence> lines = wrapName(font, itemName, wrapWidth);

        int startY = centeredStartY(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            gfx.drawString(font, lines.get(i), NAME_X, startY + i * LINE_HEIGHT, TEXT_COLOR, false);
        }
        maintainButtons(screen, pos);
    }

    /**
     * Y position for the first line so the whole block is centered between the
     * card top and the Wiki button. Keeps 1-line and 2-line entries visually balanced.
     */
    private static int centeredStartY(int lineCount) {
        int totalHeight = lineCount * LINE_HEIGHT - LINE_GAP;
        return Math.max(0, (BUTTON_Y_OFFSET - totalHeight) / 2);
    }

    /**
     * Splits the name to fit {@code wrapWidth}, capping at {@code maxLines}.
     * If the full text doesn't fit, the last visible line is truncated with an ellipsis.
     */
    private static List<FormattedCharSequence> wrapName(Font font, Component name, int wrapWidth) {
        List<FormattedCharSequence> all = font.split(name, wrapWidth);
        if (all.size() <= SkyblockWikiInfoClientRecipe.MAX_NAME_LINES) return all;

        // Overflow: keep (maxLines - 1) untouched, ellipsize the last visible line.
        List<FormattedCharSequence> trimmed = new java.util.ArrayList<>(SkyblockWikiInfoClientRecipe.MAX_NAME_LINES);
        for (int i = 0; i < SkyblockWikiInfoClientRecipe.MAX_NAME_LINES - 1; i++) trimmed.add(all.get(i));
        trimmed.add(ellipsize(font, all.get(SkyblockWikiInfoClientRecipe.MAX_NAME_LINES - 1), wrapWidth));
        return trimmed;
    }

    /** Truncates a single line to {@code wrapWidth} with a trailing ellipsis. */
    private static FormattedCharSequence ellipsize(Font font, FormattedCharSequence line, int wrapWidth) {
        int ellipsisWidth = font.width(ELLIPSIS);
        int available = Math.max(0, wrapWidth - ellipsisWidth);
        FormattedCharSequence head = FormattedCharSequence.composite(
                (FormattedCharSequence) font.substrByWidth((FormattedText) line, available));
        return FormattedCharSequence.composite(head, FormattedCharSequence.forward(ELLIPSIS, net.minecraft.network.chat.Style.EMPTY));
    }

    @Override
    @Nullable
    protected Button placeButtons(RecipeViewScreen screen, RecipePosition pos) {
        return placeWikiButton(screen, pos.left(), pos.top() + BUTTON_Y_OFFSET);
    }

    @Override
    public int getPriority() {
        return SkyblockRecipePriority.WIKI_INFO;
    }
}