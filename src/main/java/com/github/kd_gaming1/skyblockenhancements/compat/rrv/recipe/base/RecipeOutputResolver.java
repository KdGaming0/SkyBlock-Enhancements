package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base;

import com.github.kd_gaming1.skyblockenhancements.repo.item.ItemStackBuilder;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;
import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;

/**
 * Resolves the result {@link ItemStack} for a recipe entry, honouring the NEU
 * {@code overrideOutputId} and {@code count} fields.
 *
 * <p>Shared between crafting and forge parsers so the override semantics live in one place.
 */
public final class RecipeOutputResolver {

    private RecipeOutputResolver() {}

    /**
     * Returns the output stack for a recipe entry. Falls back to the owning item's stack when
     * no override is specified or the override doesn't resolve.
     */
    public static ItemStack resolve(JsonObject recipe, NeuItem owner) {
        NeuItem resolved = owner;
        if (recipe.has("overrideOutputId") && recipe.get("overrideOutputId").isJsonPrimitive()) {
            NeuItem override = NeuItemRegistry.get(recipe.get("overrideOutputId").getAsString());
            if (override != null) resolved = override;
        }

        ItemStack stack = ItemStackBuilder.build(resolved).copy();
        int count = recipe.has("count") ? recipe.get("count").getAsInt() : 1;
        if (count > 1) stack.setCount(count);
        return stack;
    }
}