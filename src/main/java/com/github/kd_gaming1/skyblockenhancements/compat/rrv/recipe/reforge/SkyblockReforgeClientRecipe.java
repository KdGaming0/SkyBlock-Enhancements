package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.reforge;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.AbstractSkyblockClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.SlotRefParser;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.render.RecipeColors;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.render.RecipeLayoutConstants;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipePriority;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipeUtil;
import com.github.kd_gaming1.skyblockenhancements.repo.item.ItemStackBuilder;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.SkyblockItemCategory;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.SkyblockRarity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side reforge template recipe. Dynamically resolves the viewed item's rarity
 * and displays stats for that rarity. Matches items by lore type rather than exact ID.
 */
public class SkyblockReforgeClientRecipe extends AbstractSkyblockClientRecipe {

    private static final int PREVIEW_SIZE = 36;
    private static final int PREVIEW_X = 4;
    private static final int PREVIEW_Y = 4;

    private static final int NAME_X_STONE = 26;
    private static final int NAME_X_BLACKSMITH = PREVIEW_X + PREVIEW_SIZE + 4;
    private static final int NAME_Y = 4;

    private static final int COST_X_STONE = 26;
    private static final int COST_X_BLACKSMITH = NAME_X_BLACKSMITH;
    private static final int COST_Y = 16;

    private static final int STATS_START_Y = 44;
    private static final int STATS_LINE_HEIGHT = 10;
    private static final int STATS_MARGIN_X = 4;

    private static final int BUTTON_ROW_Y = 78;

    private final String reforgeName;
    private final boolean isBlacksmith;
    private final String stoneInternalName;
    private final String itemType;
    private final List<String> requiredRarities;
    private final Map<String, Map<String, Double>> stats;
    private final Map<String, Integer> costs;
    private final Optional<String> ability;
    private final Map<String, String> abilitiesByRarity;
    private final List<String> specificInternalNames;
    private final List<String> specificItemIds;
    private final Optional<String> nbtModifier;

    @Nullable private List<SlotContent> cachedResults;
    private final BlacksmithPreviewRenderer blacksmithPreview;

    public SkyblockReforgeClientRecipe(SkyblockReforgeServerRecipe src) {
        super(src.getWikiUrls());
        this.reforgeName = src.getReforgeName();
        this.isBlacksmith = src.isBlacksmith();
        this.stoneInternalName = src.getStoneInternalName();
        this.itemType = src.getItemType();
        this.requiredRarities = src.getRequiredRarities();
        this.stats = src.getStats();
        this.costs = src.getCosts();
        this.ability = src.getAbility();
        this.abilitiesByRarity = src.getAbilitiesByRarity();
        this.specificInternalNames = src.getSpecificInternalNames();
        this.specificItemIds = src.getSpecificItemIds();
        this.nbtModifier = src.getNbtModifier();
        this.blacksmithPreview = isBlacksmith ? new BlacksmithPreviewRenderer() : null;
    }

    // ── ReliableClientRecipe core ──────────────────────────────────────────────

    @Override
    public ReliableClientRecipeType getViewType() {
        return SkyblockReforgeRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        if (!isBlacksmith) {
            SlotContent stone = SlotRefParser.parse(stoneInternalName);
            if (!stone.isEmpty()) {
                ctx.bindSlot(0, stone);
            }
        }
    }

    @Override
    public List<SlotContent> getIngredients() {
        return List.of();
    }

    @Override
    public List<SlotContent> getResults() {
        if (cachedResults == null) {
            cachedResults = buildResults();
        }
        return cachedResults;
    }

    @Override
    public boolean isVisualOnly() {
        return true;
    }

    @Override
    public int getPriority() {
        return SkyblockRecipePriority.REFORGE;
    }

    /**
     * Custom result matching: instead of exact item ID, checks whether the viewed item's
     * lore type matches this reforge's {@code itemType}.
     */
    @Override
    public boolean redirectsAsResult(ItemStack stack) {
        String itemId = SkyblockRecipeUtil.extractSkyblockId(stack);
        if (itemId == null) return false;
        NeuItem item = NeuItemRegistry.get(itemId);
        if (item == null) return false;
        List<String> types = ReforgeTypeResolver.resolve(item);
        return types.contains(itemType);
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Override
    public void initRecipe() {
        super.initRecipe();
        if (blacksmithPreview != null) {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (level != null) blacksmithPreview.init(level);
        }
    }

    @Override
    public void fadeRecipe() {
        super.fadeRecipe();
        if (blacksmithPreview != null) blacksmithPreview.fade();
    }

    @Override
    public void tick() {
        if (blacksmithPreview != null) blacksmithPreview.tick();
    }

    // ── Rendering ──────────────────────────────────────────────────────────────

    @Override
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos, GuiGraphics gfx,
                             int mouseX, int mouseY, float partialTicks) {
        if (isBlacksmith && blacksmithPreview != null) {
            blacksmithPreview.render(gfx, pos.left(), pos.top(), partialTicks);
        }

        ItemStack origin = screen.getMenu().getOrigin();
        ResolvedRarity resolved = resolveRarity(origin);

        renderName(gfx, pos);
        renderCost(gfx, pos, resolved);
        renderAbility(gfx, pos, resolved);
        renderStats(gfx, pos, resolved);
        maintainButtons(screen, pos);
    }

    private void renderName(GuiGraphics gfx, RecipePosition pos) {
        int nameX = isBlacksmith ? NAME_X_BLACKSMITH : NAME_X_STONE;
        int maxWidth = pos.width() - nameX - STATS_MARGIN_X;
        String label = "§e" + reforgeName + (isBlacksmith ? " §7(Blacksmith)" : "");
        Component line = SkyblockRecipeUtil.ellipsize(font(), label, maxWidth);
        gfx.drawString(font(), line, nameX, NAME_Y, RecipeColors.WHITE, true);
    }

    private void renderCost(GuiGraphics gfx, RecipePosition pos, ResolvedRarity resolved) {
        if (isBlacksmith || costs.isEmpty()) return;
        Integer cost = costs.get(resolved.rarityName());
        if (cost == null || cost == 0) return;
        int costX = isBlacksmith ? COST_X_BLACKSMITH : COST_X_STONE;
        int maxWidth = pos.width() - costX - STATS_MARGIN_X;
        String label = "§7Cost: §6" + SkyblockRecipeUtil.formatNumber(cost) + " coins";
        Component line = SkyblockRecipeUtil.ellipsize(font(), label, maxWidth);
        gfx.drawString(font(), line, costX, COST_Y, RecipeColors.COINS, true);
    }

    private void renderAbility(GuiGraphics gfx, RecipePosition pos, ResolvedRarity resolved) {
        String text = resolved.ability();
        if (text == null || text.isEmpty()) return;
        text = text.replace("\n", " ").trim();
        int abilityY = STATS_START_Y - STATS_LINE_HEIGHT;
        int maxWidth = pos.width() - STATS_MARGIN_X * 2;
        Component line = SkyblockRecipeUtil.ellipsize(font(), text, maxWidth);
        gfx.drawString(font(), line, STATS_MARGIN_X, abilityY, RecipeColors.WHITE, true);
    }

    private void renderStats(GuiGraphics gfx, RecipePosition pos, ResolvedRarity resolved) {
        Map<String, Double> rarityStats = resolved.stats();
        if (rarityStats.isEmpty()) {
            String msg = resolved.validRarity
                    ? "§8No stats for " + resolved.rarityName()
                    : "§8Not available for " + resolved.rarityName();
            gfx.drawString(font(), msg, STATS_MARGIN_X, STATS_START_Y,
                    RecipeColors.PLACEHOLDER, true);
            return;
        }

        int y = STATS_START_Y;
        int maxWidth = pos.width() - STATS_MARGIN_X * 2;
        int maxLines = (pos.height() - STATS_START_Y - RecipeLayoutConstants.WIKI_BUTTON_HEIGHT - 4) / STATS_LINE_HEIGHT;
        maxLines = Math.max(1, maxLines);

        int lineCount = 0;
        for (var entry : rarityStats.entrySet()) {
            if (lineCount >= maxLines) {
                gfx.drawString(font(), "§8...", STATS_MARGIN_X, y, RecipeColors.PLACEHOLDER, true);
                break;
            }

            String statName = formatStatName(entry.getKey());
            double value = entry.getValue();
            String valueStr = formatStatValue(value);
            String text = "§7" + statName + ": " + valueStr;

            Component line = SkyblockRecipeUtil.ellipsize(font(), text, maxWidth);
            gfx.drawString(font(), line, STATS_MARGIN_X, y, RecipeColors.WHITE, true);

            y += STATS_LINE_HEIGHT;
            lineCount++;
        }
    }

    // ── Rarity resolution ──────────────────────────────────────────────────────

    private record ResolvedRarity(String rarityName, boolean validRarity,
                                  Map<String, Double> stats, String ability) {}

    private ResolvedRarity resolveRarity(ItemStack origin) {
        String rarityName = "COMMON";
        boolean valid = true;

        if (origin != null && !origin.isEmpty()) {
            String itemId = SkyblockRecipeUtil.extractSkyblockId(origin);
            if (itemId != null) {
                NeuItem item = NeuItemRegistry.get(itemId);
                if (item != null) {
                    SkyblockRarity rarity = SkyblockItemCategory.extractRarity(item);
                    if (rarity != null) {
                        rarityName = rarity.name();
                    }
                }
            }
        }

        if (!requiredRarities.contains(rarityName)) {
            valid = false;
        }

        Map<String, Double> rarityStats = valid ? stats.getOrDefault(rarityName, Map.of()) : Map.of();
        String abilityText = null;
        if (ability.isPresent()) {
            abilityText = ability.get();
        } else if (!abilitiesByRarity.isEmpty()) {
            abilityText = abilitiesByRarity.get(rarityName);
        }

        return new ResolvedRarity(rarityName, valid, rarityStats, abilityText);
    }

    // ── Result building ────────────────────────────────────────────────────────

    private List<SlotContent> buildResults() {
        Set<Item> seenItems = new HashSet<>();
        List<ItemStack> stacks = new ArrayList<>();

        // Specific internal names (stone-specific items)
        for (String id : specificInternalNames) {
            NeuItem item = NeuItemRegistry.get(id);
            if (item != null) {
                ItemStack stack = ItemStackBuilder.build(item);
                if (!stack.isEmpty() && seenItems.add(stack.getItem())) {
                    stacks.add(stack);
                }
            }
        }

        // Specific minecraft item IDs
        if (!specificItemIds.isEmpty()) {
            for (NeuItem item : NeuItemRegistry.getAll().values()) {
                for (String targetId : specificItemIds) {
                    if (targetId.equals(item.itemId) || targetId.equals(item.snbtItemId)) {
                        ItemStack stack = ItemStackBuilder.build(item);
                        if (!stack.isEmpty() && seenItems.add(stack.getItem())) {
                            stacks.add(stack);
                        }
                        break;
                    }
                }
            }
        }

        // Category-based lookup
        List<String> loreTypes = ReforgeTypeResolver.getLoreTypesForReforgeType(itemType);
        if (!loreTypes.isEmpty()) {
            for (NeuItem item : NeuItemRegistry.getAll().values()) {
                String loreType = SkyblockItemCategory.extractLoreType(item);
                if (loreTypes.contains(loreType)) {
                    ItemStack stack = ItemStackBuilder.build(item);
                    if (!stack.isEmpty() && seenItems.add(stack.getItem())) {
                        stacks.add(stack);
                    }
                }
            }
        }

        if (stacks.isEmpty()) return List.of();
        return List.of(SlotContent.of(stacks));
    }

    // ── Formatting ─────────────────────────────────────────────────────────────

    private static String formatStatName(String key) {
        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(' ');
            String part = parts[i];
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1));
        }
        return sb.toString();
    }

    private static String formatStatValue(double value) {
        if (value == (int) value) {
            return (value >= 0 ? "§a+" : "§c") + (int) value;
        }
        return (value >= 0 ? "§a+" : "§c") + value;
    }

    // ── Buttons ────────────────────────────────────────────────────────────────

    @Override
    @Nullable
    protected Button placeButtons(RecipeViewScreen screen, RecipePosition pos) {
        int btnX = (SkyblockReforgeRecipeType.DISPLAY_WIDTH - RecipeLayoutConstants.WIKI_BUTTON_WIDTH) / 2;
        return placeWikiButton(screen, pos.left() + btnX, pos.top() + BUTTON_ROW_Y);
    }
}
