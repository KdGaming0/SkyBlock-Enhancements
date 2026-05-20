package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.craftables;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.common.recipe.ClientRecipeCache;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.injection.SkyblockRecipeIndex;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipeUtil;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Short-circuits RRV's {@code getRecipesForCraftingInput} for SkyBlock items.
 *
 * <p>RRV indexes recipes by base {@link net.minecraft.world.item.Item} type. Because
 * ~60% of SkyBlock items are {@code PLAYER_HEAD} with different NBT, RRV's inverted
 * index returns <em>every</em> skull recipe for every skull in the player's inventory.
 * It then allocates a fresh {@code ArrayList} and calls {@code removeIf} with
 * {@code redirectsAsIngredient} on thousands of recipes — causing the lag spike.
 *
 * <p>This mixin detects SkyBlock items by their internal ID and reroutes the lookup
 * through {@link SkyblockRecipeIndex}, which is keyed by SkyBlock ID and returns only
 * the relevant recipes (typically 0–10). The returned list is already filtered by
 * {@code redirectsAsIngredient}, so RRV's subsequent {@code removeIf} becomes a no-op.
 *
 * <p>Non-SkyBlock items fall through to RRV's native logic unchanged.
 */
@Mixin(ClientRecipeCache.class)
public class ClientRecipeCacheMixin {

    @Inject(
            method = "getRecipesForCraftingInput",
            at = @At("HEAD"),
            cancellable = true
    )
    private void skyblockEnhancements$fastSkyBlockLookup(
            ItemStack inputStack,
            CallbackInfoReturnable<List<ReliableClientRecipe>> cir) {

        if (inputStack.isEmpty()) return;

        // Hypixel SkyBlock does not use Polymer, so running before RRV's polymer
        // normalization (at the very start of the method) is safe and correct.
        String skyblockId = SkyblockRecipeUtil.extractSkyblockId(inputStack);
        if (skyblockId != null) {
            // SkyblockRecipeIndex returns a fresh mutable list, so RRV's removeIf is safe.
            cir.setReturnValue(SkyblockRecipeIndex.getRecipesForIngredient(inputStack));
        }
    }
}
