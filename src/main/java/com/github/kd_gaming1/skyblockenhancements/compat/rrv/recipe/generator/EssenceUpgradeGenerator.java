package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.generator;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.SlotRefParser;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.essence.SkyblockEssenceUpgradeServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.repo.hypixel.HypixelItemsRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.hypixel.HypixelItemsRegistry.HypixelUpgradeCost;
import com.github.kd_gaming1.skyblockenhancements.repo.item.StarredItemBuilder;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.item.ItemStack;

/**
 * Generates essence upgrade recipes from the Hypixel items API. One recipe per star level
 * per item with an {@code upgrade_costs} entry.
 *
 * <p>Per-star layout:
 * <ul>
 *   <li><b>Input</b>  — {@code star-1} state of the item (or the unstarred base for ★1)</li>
 *   <li><b>Output</b> — {@code star} state of the item, with stat deltas in the lore</li>
 *   <li><b>Essence</b> — required essence for this star</li>
 *   <li><b>Companions</b> — any {@code ITEM}-type costs (coins, souls, mats)</li>
 * </ul>
 */
public final class EssenceUpgradeGenerator {

    private EssenceUpgradeGenerator() {}

    /** Appends all essence upgrade recipes for every Hypixel upgrade-costs entry. */
    public static void generate(List<ReliableServerRecipe> out) {
        if (!HypixelItemsRegistry.isLoaded()) {
            LOGGER.warn("Hypixel items registry not loaded — skipping essence upgrade recipes");
            return;
        }

        int count = 0;
        for (String itemId : HypixelItemsRegistry.getAllUpgradeItemIds()) {
            NeuItem item = NeuItemRegistry.get(itemId);
            if (item == null) continue;

            List<List<HypixelUpgradeCost>> perStar = HypixelItemsRegistry.getUpgradeCosts(itemId);
            if (perStar == null) continue;

            String[] wikiUrls = item.getWikiUrls();
            for (int i = 0; i < perStar.size(); i++) {
                int star = i + 1;
                if (appendStar(out, item, perStar.get(i), star, wikiUrls)) {
                    count++;
                }
            }
        }
        LOGGER.info("Generated {} essence upgrade recipes from Hypixel API", count);
    }

    /**
     * Appends one star-level recipe. Returns {@code false} when the star lacks an essence
     * cost entry (malformed API row — should not occur in practice).
     */
    private static boolean appendStar(
            List<ReliableServerRecipe> out,
            NeuItem item,
            List<HypixelUpgradeCost> starCosts,
            int star,
            String[] wikiUrls) {

        String essenceType = null;
        int essenceAmount = 0;
        List<String> companionRefs = new ArrayList<>();

        for (HypixelUpgradeCost cost : starCosts) {
            if (cost.isEssence()) {
                essenceType = cost.essenceType();
                essenceAmount = cost.amount();
            } else {
                companionRefs.add(cost.toSlotRef());
            }
        }
        if (essenceType == null || essenceAmount <= 0) return false;

        SlotContent essence = SlotRefParser.parse(
                "ESSENCE_" + essenceType.toUpperCase(Locale.ROOT) + ":" + essenceAmount);

        SlotContent[] companions = new SlotContent[companionRefs.size()];
        for (int i = 0; i < companionRefs.size(); i++) {
            companions[i] = SlotRefParser.parse(companionRefs.get(i));
        }

        ItemStack input = StarredItemBuilder.buildInput(item, star);
        ItemStack output = StarredItemBuilder.buildOutput(item, star);

        out.add(new SkyblockEssenceUpgradeServerRecipe(
                SlotContent.of(input), SlotContent.of(output),
                essence, companions, star, essenceType, wikiUrls));
        return true;
    }
}