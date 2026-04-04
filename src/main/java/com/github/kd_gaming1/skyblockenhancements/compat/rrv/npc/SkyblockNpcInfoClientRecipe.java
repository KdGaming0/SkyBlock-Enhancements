package com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SkyblockRecipeUtil;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * Visual-only client recipe for NPC info cards. Renders the NPC head, location, lore,
 * and — at the bottom — buttons for wiki and SkyHanni navigation.
 *
 * <p>Lore lines are word-wrapped and clamped to the available vertical space between the
 * location header and the button row.
 */
public class SkyblockNpcInfoClientRecipe implements ReliableClientRecipe {

    private static final boolean SKYHANNI_PRESENT =
            FabricLoader.getInstance().isModLoaded("skyhanni");

    /** Must match {@link SkyblockNpcInfoRecipeType#getDisplayHeight()}. */
    private static final int RECIPE_HEIGHT = 100;
    private static final int BUTTON_ROW_HEIGHT = 18;
    private static final int LINE_HEIGHT = 10;

    private final SlotContent npcHead;
    private final String npcId;
    private final String npcDisplayName;
    private final String island;
    private final int x;
    private final int y;
    private final int z;
    private final String[] loreLines;
    private final String[] wikiUrls;

    private final List<Button> addedButtons = new ArrayList<>();

    public SkyblockNpcInfoClientRecipe(
            ItemStack npcHead, String npcId, String npcDisplayName, String island,
            int x, int y, int z, String[] loreLines, String[] wikiUrls) {
        this.npcHead = npcHead != null && !npcHead.isEmpty() ? SlotContent.of(npcHead) : null;
        this.npcId = npcId != null ? npcId : "";
        this.npcDisplayName = npcDisplayName != null ? npcDisplayName : "";
        this.island = island != null ? island : "";
        this.x = x;
        this.y = y;
        this.z = z;
        this.loreLines = loreLines != null ? loreLines : new String[0];
        this.wikiUrls = SkyblockRecipeUtil.sanitizeWikiUrls(wikiUrls);
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

    /** Info recipes have no ingredients — never match ingredient lookups. */
    @Override
    public boolean redirectsAsIngredient(ItemStack stack) {
        return false;
    }

    @Override
    public boolean redirectsAsResult(ItemStack stack) {
        return SkyblockRecipeUtil.matchesAny(stack, getResults());
    }

    @Override
    public void renderRecipe(
            RecipeViewScreen screen, RecipePosition pos, GuiGraphics gfx,
            int mouseX, int mouseY, float partialTicks) {

        Font font = Minecraft.getInstance().font;
        int textX = 22; // Right of the 20×20 NPC head slot
        int lineY = 2;
        int maxTextY = RECIPE_HEIGHT - BUTTON_ROW_HEIGHT;

        // Location header
        if (!island.isEmpty()) {
            gfx.drawString(font,
                    Component.literal("§7" + formatIsland(island)),
                    textX, lineY, 0xFFAAAAAA, false);
            lineY += LINE_HEIGHT;
            gfx.drawString(font,
                    Component.literal("§8" + x + ", " + y + ", " + z),
                    textX, lineY, 0xFF888888, false);
            lineY += LINE_HEIGHT + 2;
        }

        // Lore — word-wrapped and clamped so long descriptions don't overflow.
        int maxTextWidth = 130 - textX;
        for (String line : loreLines) {
            if (line == null || line.isEmpty()) continue;
            if (lineY + LINE_HEIGHT > maxTextY) break;

            List<FormattedCharSequence> wrapped = font.split(Component.literal(line), maxTextWidth);
            for (FormattedCharSequence wrappedLine : wrapped) {
                if (lineY + LINE_HEIGHT > maxTextY) break;
                gfx.drawString(font, wrappedLine, textX, lineY, 0xFFFFFFFF, false);
                lineY += LINE_HEIGHT;
            }
        }

        // Buttons anchored to the bottom of the recipe area.
        if (!buttonsStillInScreen(screen)) {
            addedButtons.clear();
            addButtons(screen, pos);
        }
    }

    private boolean buttonsStillInScreen(RecipeViewScreen screen) {
        if (addedButtons.isEmpty()) return false;
        return SkyblockRecipeUtil.containsAllByIdentity(screen.children(), addedButtons);
    }


    private void addButtons(RecipeViewScreen screen, RecipePosition pos) {
        int btnY = pos.top() + RECIPE_HEIGHT - BUTTON_ROW_HEIGHT + 3;
        int btnX = pos.left() + 2;

        Button wikiBtn = SkyblockRecipeUtil.addWikiButton(screen, wikiUrls, btnX, btnY);
        if (wikiBtn != null) {
            addedButtons.add(wikiBtn);
            btnX += 60;
        }

        // SkyHanni /shnav integration — only shown when SkyHanni is installed.
        if (SKYHANNI_PRESENT) {
            String cleanName = cleanNpcName(npcDisplayName);
            Button navBtn = Button.builder(
                            Component.literal("⬈ Navigate"),
                            b -> sendShNavCommand(cleanName))
                    .pos(btnX, btnY)
                    .size(60, 12)
                    .build();
            screen.addRecipeWidget(navBtn);
            addedButtons.add(navBtn);
        }
    }

    private static void sendShNavCommand(String npcName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.getConnection() != null) {
            mc.getConnection().sendCommand("shnav " + npcName);
        }
    }

    /** Strips formatting codes and the {@code " (NPC)"} suffix from a display name. */
    private static String cleanNpcName(String displayName) {
        String clean = displayName.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
        if (clean.endsWith(" (NPC)")) clean = clean.substring(0, clean.length() - 6);
        return clean.trim();
    }

    /** Converts {@code "crimson_isle"} → {@code "Crimson Isle"}. */
    private static String formatIsland(String island) {
        String[] words = island.split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
        }
        return sb.toString();
    }
}