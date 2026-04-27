package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.drops;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.AbstractSkyblockClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipePriority;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipeUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side drop recipe. Uses {@link AbstractSkyblockClientRecipe} for button lifecycle
 * but manages its own entity preview spawn / dispose in {@link #initRecipe} / {@link #fadeRecipe}.
 */
public class SkyblockDropsClientRecipe extends AbstractSkyblockClientRecipe {

    private static final int MAX_DROPS = 12;
    private static final int BUTTON_ROW_Y_OFFSET = SkyblockDropsRecipeType.WIKI_BUTTON_TOP;
    private static final int NAME_CAPTION_Y      = SkyblockDropsRecipeType.NAME_CAPTION_TOP;
    private static final int NAME_COLOR        = 0xFF404040;
    private static final int NAME_SIDE_PADDING = 4;
    private static final String ELLIPSIS       = "...";

    /** De-duplicates warn logs so unresolved refs don't spam once per scroll tick. */
    private static final Set<String> LOGGED_UNRESOLVED = ConcurrentHashMap.newKeySet();

    private final String mobName;
    private final String[] chances;
    private final List<SlotContent> drops;

    @Nullable private final MobPreview preview;

    /** Spawned on {@link #initRecipe()} only when the preview requires a vanilla mob. */
    @Nullable private LivingEntity mountEntity;
    @Nullable private LivingEntity riderEntity;

    private int animationTick;
    private boolean previewHovered;

    public SkyblockDropsClientRecipe(SkyblockDropsServerRecipe src) {
        super(src.getWikiUrls());
        this.mobName  = src.getMobName() != null ? src.getMobName() : "";
        this.chances  = src.getChances();
        this.drops    = buildDropsList(src.getDrops());

        MobPreview resolved = MobRenderResolver.resolve(src.getRenderRef());
        if (resolved == null) logUnresolvedOnce(src.getRenderRef());
        this.preview = resolved;
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
            ctx.addAdditionalStackModifier(i, (stack, tooltip) ->
                    tooltip.add(Component.literal("§7Drop chance: §e" + chance)));
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
        if (preview == null || !preview.needsLivingEntity()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        mountEntity = spawnForLayer(level, preview.entityType());
        MobPreview rider = preview.rider();
        if (rider != null) {
            riderEntity = spawnForLayer(level, rider.entityType());
            if (riderEntity != null && mountEntity != null) {
                riderEntity.startRiding(mountEntity);
            }
        }
    }

    /** Creates and configures a preview entity, or returns {@code null} if the type is absent/invalid. */
    @Nullable
    private static LivingEntity spawnForLayer(ClientLevel level, @Nullable EntityType<?> type) {
        if (type == null) return null;
        Entity entity = type.create(level, EntitySpawnReason.LOAD);
        if (!(entity instanceof LivingEntity living)) return null;
        living.setYBodyRot(30.0F);
        living.setYHeadRot(30.0F);
        return living;
    }

    @Override
    public void fadeRecipe() {
        super.fadeRecipe();
        disposeEntities();
    }

    private void disposeEntities() {
        if (mountEntity != null) {
            mountEntity.remove(Entity.RemovalReason.DISCARDED);
            mountEntity = null;
        }
        if (riderEntity != null) {
            riderEntity.remove(Entity.RemovalReason.DISCARDED);
            riderEntity = null;
        }
    }

    @Override
    public void tick() {
        if (previewHovered) return;
        animationTick++;
        if (animationTick >= MobPreviewRenderer.rotationPeriod()) animationTick = 0;
    }

    // ── Rendering ──────────────────────────────────────────────────────────────

    @Override
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos, GuiGraphics gfx,
                             int mouseX, int mouseY, float partialTicks) {
        previewHovered = MobPreviewRenderer.isPointInPreviewBox(mouseX, mouseY);

        if (preview != null) {
            MobPreviewRenderer.render(preview, gfx, pos.left(), pos.top(),
                    mountEntity, riderEntity, animationTick, partialTicks);
        } else {
            MobPreviewRenderer.renderPlaceholder(gfx, pos.left(), pos.top());
        }

        renderMobName(gfx, pos);
        renderHoverTooltipIfNeeded(gfx, screen, pos, mouseX, mouseY);
        maintainButtons(screen, pos);
    }

    private void renderMobName(GuiGraphics gfx, RecipePosition pos) {
        if (mobName.isEmpty()) return;

        Font font = Minecraft.getInstance().font;
        int maxWidth = pos.width() - NAME_SIDE_PADDING * 2;
        Component line = fitToWidth(font, mobName, maxWidth);

        int textWidth = font.width(line);
        int x = pos.left() + NAME_SIDE_PADDING + (maxWidth - textWidth) / 2;
        int y = pos.top() + NAME_CAPTION_Y;
        gfx.drawString(font, line, x, y, NAME_COLOR, true);
    }

    private static Component fitToWidth(Font font, String raw, int maxWidth) {
        Component full = Component.literal(raw);
        if (font.width(full) <= maxWidth) return full;

        FormattedText ellipsis = FormattedText.of(ELLIPSIS);
        int ellipsisWidth = font.width(ellipsis);
        int available = Math.max(0, maxWidth - ellipsisWidth);
        String trimmed = font.substrByWidth(full, available).getString();
        return Component.literal(trimmed + ELLIPSIS);
    }

    private void renderHoverTooltipIfNeeded(GuiGraphics gfx, RecipeViewScreen screen,
                                            RecipePosition pos, int mouseX, int mouseY) {
        if (!previewHovered || mobName.isEmpty()) return;

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
        return placeWikiButton(screen, pos.left(), pos.top() + BUTTON_ROW_Y_OFFSET);
    }
}
