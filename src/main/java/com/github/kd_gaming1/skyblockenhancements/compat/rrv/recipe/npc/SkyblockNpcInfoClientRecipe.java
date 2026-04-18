package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipePriority;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.AbstractSkyblockClientRecipe;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class SkyblockNpcInfoClientRecipe extends AbstractSkyblockClientRecipe
        implements ReliableClientRecipe {

    /** Adds a §⬈ Navigate§ button that delegates to SkyHanni's {@code /shnav} command. */
    private static final boolean SKYHANNI_PRESENT =
            FabricLoader.getInstance().isModLoaded("skyhanni");

    private static final int RECIPE_HEIGHT     = 100;
    private static final int BUTTON_ROW_HEIGHT = 18;
    private static final int LINE_HEIGHT       = 10;
    private static final int TEXT_X            = 22;
    private static final int TEXT_RIGHT_LIMIT  = 130;
    private static final int BUTTON_W          = 56;
    private static final int BUTTON_H          = 12;
    private static final int NAV_BUTTON_W      = 60;

    private final @Nullable SlotContent npcHead;
    private final String npcId;
    private final String npcDisplayName;
    private final String island;
    private final int x, y, z;
    private final String[] loreLines;

    public SkyblockNpcInfoClientRecipe(ItemStack npcHead, String npcId, String npcDisplayName,
                                       String island, int x, int y, int z,
                                       String[] loreLines, String[] wikiUrls) {
        super(wikiUrls);
        this.npcHead = (npcHead != null && !npcHead.isEmpty()) ? SlotContent.of(npcHead) : null;
        this.npcId = npcId != null ? npcId : "";
        this.npcDisplayName = npcDisplayName != null ? npcDisplayName : "";
        this.island = island != null ? island : "";
        this.x = x; this.y = y; this.z = z;
        this.loreLines = loreLines != null ? loreLines : new String[0];
    }

    public String getNpcId() {
        return npcId;
    }

    @Override
    public ReliableClientRecipeType getViewType() {
        return SkyblockNpcInfoRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        if (npcHead != null) ctx.bindSlot(0, npcHead);
    }

    @Override
    public List<SlotContent> getIngredients() {
        return List.of();
    }

    @Override
    public List<SlotContent> getResults() {
        return npcHead != null ? List.of(npcHead) : List.of();
    }

    @Override
    public boolean isVisualOnly() {
        return true;
    }

    @Override
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos, GuiGraphics gfx,
                             int mouseX, int mouseY, float partialTicks) {
        Font font = Minecraft.getInstance().font;
        int cursorY = renderLocationHeader(gfx, font);
        renderLoreLines(gfx, font, cursorY);
        maintainButtons(screen, pos);
    }

    private int renderLocationHeader(GuiGraphics gfx, Font font) {
        if (island.isEmpty()) return 2;

        int cursorY = 2;
        gfx.drawString(font, Component.literal("§7" + formatIsland(island)),
                TEXT_X, cursorY, 0xFFAAAAAA, false);
        cursorY += LINE_HEIGHT;
        gfx.drawString(font, Component.literal("§8" + x + ", " + y + ", " + z),
                TEXT_X, cursorY, 0xFF888888, false);
        return cursorY + LINE_HEIGHT + 2;
    }

    private void renderLoreLines(GuiGraphics gfx, Font font, int cursorY) {
        int maxTextY = RECIPE_HEIGHT - BUTTON_ROW_HEIGHT;
        int maxTextWidth = TEXT_RIGHT_LIMIT - TEXT_X;

        for (String line : loreLines) {
            if (line == null || line.isEmpty()) continue;
            if (cursorY + LINE_HEIGHT > maxTextY) break;

            for (FormattedCharSequence wrapped : font.split(Component.literal(line), maxTextWidth)) {
                if (cursorY + LINE_HEIGHT > maxTextY) break;
                gfx.drawString(font, wrapped, TEXT_X, cursorY, 0xFFFFFFFF, false);
                cursorY += LINE_HEIGHT;
            }
        }
    }

    @Override
    @Nullable
    protected Button placeButtons(RecipeViewScreen screen, RecipePosition pos) {
        int btnY = pos.top() + RECIPE_HEIGHT - BUTTON_ROW_HEIGHT + 3;
        int btnX = pos.left() + 2;

        Button sentinel = placeWikiButton(screen, btnX, btnY);
        if (sentinel != null) btnX += BUTTON_W + 4;

        if (SKYHANNI_PRESENT) {
            Button navBtn = Button.builder(
                            Component.literal("⬈ Navigate"),
                            b -> sendNavigateCommand(npcDisplayName))
                    .pos(btnX, btnY)
                    .size(NAV_BUTTON_W, BUTTON_H)
                    .build();
            screen.addRecipeWidget(navBtn);
            if (sentinel == null) sentinel = navBtn;
        }

        return sentinel;
    }

    private static void sendNavigateCommand(String displayName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        mc.getConnection().sendCommand("shnav " + stripFormatting(displayName));
    }

    private static String stripFormatting(String displayName) {
        String clean = displayName.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
        if (clean.endsWith(" (NPC)")) clean = clean.substring(0, clean.length() - 6);
        return clean.trim();
    }

    private static String formatIsland(String island) {
        String[] words = island.split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    @Override
    public int getPriority() {
        return SkyblockRecipePriority.NPC_INFO;
    }
}