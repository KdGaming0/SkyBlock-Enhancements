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
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.repo.item.ItemStackBuilder;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side reforge recipe card for one specific rarity.
 *
 * <p>Tall card showing the full list of stat boosts this reforge gives to an item of the
 * recipe's fixed rarity. Ability text and long stat lines wrap onto multiple lines instead of
 * being truncated.
 *
 * <p>{@link #redirectsAsResult} filters by rarity + item type, so clicking a reforgable item
 * (e.g. a sword) shows only matching reforge recipes.
 * <p>{@link #redirectsAsIngredient} filters by stone internal name, so clicking a reforge stone
 * (e.g. AMBER_MATERIAL) shows all rarity variants of the reforge it provides.
 *
 * <p>Result stacks are pre-computed during recipe generation and stored in the server recipe,
 * so {@link #getResults} never scans the full item registry.
 */
public class SkyblockReforgeClientRecipe extends AbstractSkyblockClientRecipe {

    private static final int ICON_X = 4;
    private static final int ICON_Y = 4;
    private static final int ICON_SIZE = 16;

    private static final int NAME_X = 24;
    private static final int NAME_Y = 4;

    private static final int SUBTITLE_X = 24;
    private static final int SUBTITLE_Y = 16;

    private static final int STATS_START_Y = 30;
    private static final int LINE_HEIGHT = 9;
    private static final int TEXT_MARGIN_X = 4;
    private static final int BUTTON_ROW_Y = 130;

    private final String reforgeName;
    private final boolean isBlacksmith;
    private final String stoneInternalName;
    private final String itemType;
    private final String rarity;
    private final int rarityOrdinal;
    private final List<String> requiredRarities;
    private final Map<String, Double> stats;
    private final int cost;
    private final Optional<String> ability;
    private final List<String> specificInternalNames;
    private final List<String> specificItemIds;
    private final Optional<String> nbtModifier;

    /** Pre-computed internal names of items that match this reforge (all rarities). */
    private final List<String> resultInternalNames;
    /** Set view of {@link #resultInternalNames} for O(1) {@link #redirectsAsResult} checks. */
    private final Set<String> resultIdSet;
    /** Pre-computed stone slot content; empty for blacksmith reforges. */
    private final SlotContent cachedStone;
    /** Item IDs from {@link #resultInternalNames} that also match this recipe's rarity. */
    private final Set<String> rarityFilteredIdSet;

    private final String crafttext;
    private final boolean hasCrafttext;
    @Nullable private Component cachedTooltipLine;
    private final RecipeViewMenu.AdditionalStackModifier requirementModifier;

    @Nullable private List<SlotContent> cachedResults;
    /** Cached render artefacts to avoid re-computing text layout every frame. */
    @Nullable private Component cachedName;
    @Nullable private Component cachedSubtitle;
    @Nullable private List<String> cachedWrappedStatLines;

    public SkyblockReforgeClientRecipe(SkyblockReforgeServerRecipe src) {
        super(src.getWikiUrls());
        this.reforgeName = src.getReforgeName();
        this.isBlacksmith = src.isBlacksmith();
        this.stoneInternalName = src.getStoneInternalName();
        this.itemType = src.getItemType();
        this.rarity = src.getRarity();
        this.rarityOrdinal = rarityOrdinal(this.rarity);
        this.requiredRarities = src.getRequiredRarities();
        this.stats = src.getStats();
        this.cost = src.getCost();
        this.ability = src.getAbility();
        this.specificInternalNames = src.getSpecificInternalNames();
        this.specificItemIds = src.getSpecificItemIds();
        this.nbtModifier = src.getNbtModifier();
        this.resultInternalNames = src.getResultInternalNames();
        this.resultIdSet = !resultInternalNames.isEmpty()
                ? Set.copyOf(resultInternalNames)
                : Collections.emptySet();
        this.cachedStone = (!isBlacksmith && !stoneInternalName.isEmpty())
                ? SlotRefParser.parse(stoneInternalName)
                : SlotRefParser.empty();
        this.rarityFilteredIdSet = buildRarityFilteredSet(resultInternalNames, rarity);
        this.crafttext = src.getCrafttext();
        this.hasCrafttext = !this.crafttext.isEmpty();
        this.requirementModifier = hasCrafttext ? this::appendRequirementTooltip : null;
    }

    private static Set<String> buildRarityFilteredSet(List<String> resultNames, String recipeRarity) {
        if (resultNames.isEmpty()) return Collections.emptySet();
        Set<String> set = new java.util.HashSet<>();
        for (String id : resultNames) {
            NeuItem item = NeuItemRegistry.get(id);
            if (item != null && item.rarity != null && item.rarity.name().equals(recipeRarity)) {
                set.add(id);
            }
        }
        return Collections.unmodifiableSet(set);
    }

    // ── ReliableClientRecipe core ──────────────────────────────────────────────

    @Override
    public ReliableClientRecipeType getViewType() {
        return SkyblockReforgeRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        if (!isBlacksmith) {
            bindOptional(ctx, 0, cachedStone);
        }
        if (requirementModifier != null && SkyblockEnhancementsConfig.showCollectionRequirements && !cachedStone.isEmpty()) {
            ctx.addAdditionalStackModifier(0, requirementModifier);
        }
    }

    private void appendRequirementTooltip(ItemStack stack, List<Component> tooltip) {
        tooltip.addLast(Component.empty());
        tooltip.addLast(requirementTooltipLine());
    }

    private Component requirementTooltipLine() {
        Component cached = cachedTooltipLine;
        if (cached != null) return cached;
        cached = Component.literal("§cRequirement: §e" + SkyblockRecipeUtil.formatCrafttext(crafttext));
        cachedTooltipLine = cached;
        return cached;
    }

    @Override
    public List<SlotContent> getIngredients() {
        List<SlotContent> ingredients = new ArrayList<>();
        ingredients.addAll(getResults());
        if (!isBlacksmith && !cachedStone.isEmpty()) {
            ingredients.add(cachedStone);
        }
        return ingredients;
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

    @Override
    public boolean redirectsAsResult(ItemStack stack) {
        String itemId = SkyblockRecipeUtil.extractSkyblockId(stack);
        return itemId != null && rarityFilteredIdSet.contains(itemId);
    }

    @Override
    public boolean redirectsAsIngredient(ItemStack stack) {
        String itemId = SkyblockRecipeUtil.extractSkyblockId(stack);
        if (itemId == null) return false;

        if (rarityFilteredIdSet.contains(itemId)) return true;
        if (!isBlacksmith && itemId.equals(stoneInternalName)) return true;

        return false;
    }

    // ── Rendering ──────────────────────────────────────────────────────────────

    @Override
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos, GuiGraphics gfx,
                             int mouseX, int mouseY, float partialTicks) {
        renderIconFallback(gfx);
        renderName(gfx, pos);
        renderSubtitle(gfx, pos);
        renderStats(gfx, pos);
        if (hasCrafttext && SkyblockEnhancementsConfig.showCollectionRequirements) {
            renderRequirementIndicator(gfx);
            renderNameTooltipIfHovered(gfx, screen, pos, mouseX, mouseY);
        }
        maintainButtons(screen, pos);
    }

    private void renderIconFallback(GuiGraphics gfx) {
        if (isBlacksmith) {
            gfx.renderItem(new ItemStack(Items.ANVIL), ICON_X, ICON_Y);
        }
    }

    private void renderName(GuiGraphics gfx, RecipePosition pos) {
        Component line = cachedName;
        if (line == null) {
            String rarityLabel = rarityColorCode() + rarity.replace("_", " ");
            String label = "§e" + reforgeName + " §7- " + rarityLabel;
            line = SkyblockRecipeUtil.ellipsize(font(), label, pos.width() - NAME_X - TEXT_MARGIN_X);
            cachedName = line;
        }
        gfx.drawString(font(), line, NAME_X, NAME_Y, RecipeColors.WHITE, true);
    }

    private void renderSubtitle(GuiGraphics gfx, RecipePosition pos) {
        Component line = cachedSubtitle;
        if (line == null) {
            String label;
            if (isBlacksmith) {
                label = "§7Blacksmith";
            } else if (cost > 0) {
                label = "§7Cost: §6" + SkyblockRecipeUtil.formatNumber(cost) + " coins";
            } else {
                return;
            }
            line = SkyblockRecipeUtil.ellipsize(font(), label, pos.width() - SUBTITLE_X - TEXT_MARGIN_X);
            cachedSubtitle = line;
        }
        gfx.drawString(font(), line, SUBTITLE_X, SUBTITLE_Y, RecipeColors.COINS, true);
    }

    private void renderStats(GuiGraphics gfx, RecipePosition pos) {
        if (stats.isEmpty() && ability.isEmpty()) {
            gfx.drawString(font(), "§8No stats for " + rarity,
                    TEXT_MARGIN_X, STATS_START_Y, RecipeColors.PLACEHOLDER, true);
            return;
        }

        int maxWidth = pos.width() - TEXT_MARGIN_X * 2;
        List<String> lines = cachedWrappedStatLines;
        if (lines == null) {
            lines = buildWrappedStatLines(maxWidth);
            cachedWrappedStatLines = lines;
        }

        int y = STATS_START_Y;
        for (String line : lines) {
            gfx.drawString(font(), line, TEXT_MARGIN_X, y, RecipeColors.WHITE, true);
            y += LINE_HEIGHT;
        }
    }

    private List<String> buildWrappedStatLines(int maxWidth) {
        List<String> out = new ArrayList<>();
        if (ability.isPresent() && !ability.get().isBlank()) {
            String text = ability.get().replace("\n", " ").trim();
            out.addAll(wrapText(font(), text, maxWidth));
        }
        for (var entry : stats.entrySet()) {
            String text = "§7" + formatStatName(entry.getKey()) + ": " + formatStatValue(entry.getValue());
            out.addAll(wrapText(font(), text, maxWidth));
        }
        return List.copyOf(out);
    }

    private void renderRequirementIndicator(GuiGraphics gfx) {
        gfx.drawString(font(), "§c!", ICON_X + ICON_SIZE - 2, ICON_Y - 2, RecipeColors.WHITE, false);
    }

    private void renderNameTooltipIfHovered(GuiGraphics gfx, RecipeViewScreen screen,
                                            RecipePosition pos, int mouseX, int mouseY) {
        int nameMaxX = pos.width() - TEXT_MARGIN_X;
        if (mouseX < NAME_X || mouseX >= nameMaxX
                || mouseY < NAME_Y || mouseY >= NAME_Y + font().lineHeight) {
            return;
        }
        gfx.setComponentTooltipForNextFrame(font(),
                List.of(requirementTooltipLine()),
                pos.left() + mouseX,
                pos.top() + mouseY);
    }

    // ── Text wrapping ──────────────────────────────────────────────────────────

    /**
     * Splits {@code text} into lines that each fit within {@code maxWidth} pixels.
     * Prefers splitting at word boundaries; falls back to splitting within a word.
     * Preserves Minecraft § colour/formatting codes across line breaks.
     */
    private static List<String> wrapText(Font font, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text.isEmpty()) return lines;

        String remaining = text;

        while (!remaining.isEmpty()) {
            int fit = fitLength(font, remaining, maxWidth);
            if (fit <= 0) fit = 1;

            int splitAt = fit;
            if (fit < remaining.length()) {
                // Walk back to the last space for a clean word break
                while (splitAt > 0 && remaining.charAt(splitAt - 1) != ' ') {
                    splitAt--;
                }
                if (splitAt == 0) {
                    splitAt = fit; // No space found — split mid-word
                }
            }

            String line = remaining.substring(0, splitAt).stripTrailing();
            if (!line.isEmpty()) {
                lines.add(line);
            }
            remaining = remaining.substring(splitAt).stripLeading();
        }

        return lines;
    }

    /** Returns the number of leading characters of {@code text} that fit in {@code maxWidth}. */
    private static int fitLength(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text.length();
        }

        int lo = 0;
        int hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (font.width(text.substring(0, mid)) <= maxWidth) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    // ── Rarity colour codes ────────────────────────────────────────────────────

    private String rarityColorCode() {
        return switch (rarity) {
            case "COMMON"       -> "§f";
            case "UNCOMMON"     -> "§a";
            case "RARE"         -> "§9";
            case "EPIC"         -> "§5";
            case "LEGENDARY"    -> "§6";
            case "MYTHIC"       -> "§d";
            case "DIVINE"       -> "§b";
            case "SPECIAL"      -> "§c";
            case "VERY_SPECIAL" -> "§c";
            case "SUPREME"      -> "§4";
            default             -> "§7";
        };
    }

    // ── Result building ────────────────────────────────────────────────────────

    private List<SlotContent> buildResults() {
        if (resultInternalNames.isEmpty()) return List.of();

        Set<Item> seenItems = Collections.newSetFromMap(new IdentityHashMap<>());
        List<ItemStack> stacks = new ArrayList<>(resultInternalNames.size());

        for (String id : resultInternalNames) {
            NeuItem item = NeuItemRegistry.get(id);
            if (item == null) continue;
            ItemStack stack = ItemStackBuilder.build(item);
            if (!stack.isEmpty() && seenItems.add(stack.getItem())) {
                stacks.add(stack);
            }
        }

        return stacks.isEmpty() ? List.of() : List.of(SlotContent.of(stacks));
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public String getReforgeName() {
        return reforgeName;
    }

    public String getRarity() {
        return rarity;
    }

    public int getRarityOrdinal() {
        return rarityOrdinal;
    }

    private static int rarityOrdinal(String rarity) {
        return switch (rarity) {
            case "COMMON" -> 0;
            case "UNCOMMON" -> 1;
            case "RARE" -> 2;
            case "EPIC" -> 3;
            case "LEGENDARY" -> 4;
            case "MYTHIC" -> 5;
            case "DIVINE" -> 6;
            case "SPECIAL" -> 7;
            case "VERY_SPECIAL" -> 8;
            case "SUPREME" -> 9;
            default -> -1;
        };
    }

    public boolean isBlacksmith() {
        return isBlacksmith;
    }

    public String getStoneInternalName() {
        return stoneInternalName;
    }

    public List<String> getResultInternalNames() {
        return resultInternalNames;
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
            if (part.length() > 1) sb.append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    private static String formatStatValue(double value) {
        String prefix = value >= 0 ? "§a+" : "§c";
        if (value == (int) value) {
            return prefix + (int) value;
        }
        return prefix + value;
    }

    // ── Buttons ────────────────────────────────────────────────────────────────

    @Override
    @Nullable
    protected AbstractButton placeButtons(RecipeViewScreen screen, RecipePosition pos) {
        int btnX = (SkyblockReforgeRecipeType.DISPLAY_WIDTH - RecipeLayoutConstants.WIKI_BUTTON_WIDTH) / 2;
        return placeWikiButton(screen, pos.left() + btnX, pos.top() + BUTTON_ROW_Y);
    }
}
