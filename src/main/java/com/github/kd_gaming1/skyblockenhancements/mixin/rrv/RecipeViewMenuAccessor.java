package com.github.kd_gaming1.skyblockenhancements.mixin.rrv;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the currently displayed recipes for page-seek after menu construction. */
@Mixin(RecipeViewMenu.class)
public interface RecipeViewMenuAccessor {

    @Accessor(value = "currentDisplay", remap = false)
    List<ReliableClientRecipe> sbe$getCurrentDisplay();
}