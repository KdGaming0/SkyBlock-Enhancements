package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.drops;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.AbstractSkyblockClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.render.RecipeColors;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.render.RecipeLayoutConstants;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipePriority;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipeUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side drop recipe. Delegates mob-preview entity lifecycle to
 * {@link MobPreviewController} so this class only handles slots, text, and buttons.
 */
public class SkyblockDropsClientRecipe extends AbstractSkyblockClientRecipe {

    private static final int MAX_DROPS = 12;
    private static final int BUTTON_ROW_Y_OFFSET = SkyblockDropsRecipeType.WIKI_BUTTON_TOP;
    private static final int NAME_CAPTION_Y      = SkyblockDropsRecipeType.NAME_CAPTION_TOP;
    private static final int NAME_SIDE_PADDING = 4;

    /** De-duplicates warn logs so unresolved refs don't spam once per scroll tick. */
    private static final Set<String> LOGGED_UNRESOLVED = ConcurrentHashMap.newKeySet();

    private final String mobName;
    private final String[] chances;
    private final List<SlotContent> drops;
    private final MobPreviewController previewController;

    public SkyblockDropsClientRecipe(SkyblockDropsServerRecipe src) {
        super(src.getWikiUrls());
        this.mobName  = src.getMobName() != null ? src.getMobName() : "";
        this.chances  = src.getChances();
        this.drops    = buildDropsList(src.getDrops());

        MobPreview resolved = MobRenderResolver.resolve(src.getRenderRef());
        if (resolved == null) logUnresolvedOnce(src.getRenderRef());
        this.previewController = new MobPreviewController(resolved);
    }

    private static List<SlotContent> buildDropsList(SlotContent[] rawDrops) {
        int slotCount = SkyblockDropsRecipeType.INSTANCE.getSlotCount();
        List<SlotContent> out = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            out.add(i < rawDrops.length && rawDrops[i] != null ? rawDrops[i] : SlotContent.of());
        }
        return out;
    }

    private static void logUnresolvedOnce(@Nullable String renderRef) {
        String key = renderRef != null ? renderRef : "<empty>";
        if (LOGGED_UNRESOLVED.add(key)) {
            LOGGER.debug("Unresolved drop-recipe render ref '{}' — placeholder will be drawn.", key);
        }
    }

    // ── ReliableClientRecipe core ──────────────────────────────────────────────

    @Override
    public ReliableClientRecipeType getViewType() {
        return SkyblockDropsRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        int limit = Math.min(drops.size(), MAX_DROPS);
        for (int i = 0; i < limit; i++) {
            ctx.bindSlot(i, drops.get(i));
        }
        attachChanceTooltips(ctx);
    }

    private void attachChanceTooltips(RecipeViewMenu.SlotFillContext ctx) {
        int limit = Math.min(chances != null ? chances.length : 0, MAX_DROPS);
        for (int i = 0; i < limit; i++) {
            String chance = chances[i];
            if (chance == null || chance.isEmpty()) continue;
            ctx.addAdditionalStackModifier(i, (stack, tooltip) -> {
                tooltip.addLast(Component.literal(""));
                tooltip.addLast(Component.literal("§7Drop chance: §e§l" + chance));
            });
        }
    }

    @Override
    public List<SlotContent> getIngredients() {
        return List.of();
    }

    @Override
    public List<SlotContent> getResults() {
        return drops;
    }

    @Override
    public boolean isVisualOnly() {
        return true;
    }

    @Override
    public int getPriority() {
        return SkyblockRecipePriority.DROPS;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Override
    public void initRecipe() {
        super.initRecipe();
        var level = Minecraft.getInstance().level;
        if (level != null) previewController.init(level);
    }

    @Override
    public void fadeRecipe() {
        super.fadeRecipe();
        previewController.fade();
    }

    @Override
    public void tick() {
        previewController.tick();
    }

    // ── Rendering ──────────────────────────────────────────────────────────────

    @Override
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos, GuiGraphics gfx,
                             int mouseX, int mouseY, float partialTicks) {
        previewController.render(gfx, pos.left(), pos.top(), mouseX, mouseY, partialTicks);

        renderMobName(gfx, pos);
        renderHoverTooltipIfNeeded(gfx, screen, pos, mouseX, mouseY);
        maintainButtons(screen, pos);
    }

    private void renderMobName(GuiGraphics gfx, RecipePosition pos) {
        if (mobName.isEmpty()) return;

        int maxWidth = pos.width() - NAME_SIDE_PADDING * 2;
        Component line = SkyblockRecipeUtil.ellipsize(font(), mobName, maxWidth);

        int textWidth = font().width(line);
        int x = NAME_SIDE_PADDING + (maxWidth - textWidth) / 2;

        gfx.drawString(font(), line, x, NAME_CAPTION_Y, RecipeColors.WHITE, true);
    }

    private void renderHoverTooltipIfNeeded(GuiGraphics gfx, RecipeViewScreen screen,
                                            RecipePosition pos, int mouseX, int mouseY) {
        if (!previewController.isHovered() || mobName.isEmpty()) return;

        Component tip = Component.literal(mobName).withStyle(ChatFormatting.GOLD);
        gfx.setComponentTooltipForNextFrame(
                screen.getFont(),
                List.of(tip),
                pos.left() + mouseX,
                pos.top() + mouseY);
    }

    // ── Buttons ────────────────────────────────────────────────────────────────

    @Override
    @Nullable
    protected Button placeButtons(RecipeViewScreen screen, RecipePosition pos) {
        int btnX = (SkyblockDropsRecipeType.displayWidth() - RecipeLayoutConstants.WIKI_BUTTON_WIDTH) / 2;
        return placeWikiButton(screen, pos.left() + btnX, pos.top() + BUTTON_ROW_Y_OFFSET);
    }
}
