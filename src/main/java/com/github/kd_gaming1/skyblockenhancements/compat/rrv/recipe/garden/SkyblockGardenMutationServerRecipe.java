package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.garden;

import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.AbstractSkyblockServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.SlotRefParser;
import com.github.kd_gaming1.skyblockenhancements.repo.item.ItemStackBuilder;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side garden mutation recipe.
 *
 * <p>Stores only the {@code mutationId} in NBT; the full layout is looked up from
 * {@link GardenMutationRegistry} on deserialization. This keeps network payloads tiny.
 *
 * <p>{@link #getIngredients()} returns all INGREDIENT cells so that pressing R on an
 * ingredient item shows mutations that require it. {@link #getResults()} returns the
 * TARGET cell so the mutation is found when pressing R on the mutation item itself.
 */
public class SkyblockGardenMutationServerRecipe extends AbstractSkyblockServerRecipe {

    public static final ReliableServerRecipeType<SkyblockGardenMutationServerRecipe> TYPE =
            ReliableServerRecipeType.register(
                    Identifier.fromNamespaceAndPath("skyblock_enhancements", "garden_mutation"),
                    () -> new SkyblockGardenMutationServerRecipe("", new String[0]));

    private static final String KEY_MUTATION_ID = "mid";

    private String mutationId;
    @Nullable private volatile List<SlotContent> cachedIngredients;
    @Nullable private volatile List<SlotContent> cachedResults;

    public SkyblockGardenMutationServerRecipe(String mutationId, String[] wikiUrls) {
        super(wikiUrls);
        this.mutationId = mutationId != null ? mutationId : "";
    }

    @Override
    protected ReliableServerRecipeType<?> getType() {
        return TYPE;
    }

    @Override
    protected void writeFields(CompoundTag tag) {
        if (!mutationId.isEmpty()) {
            tag.putString(KEY_MUTATION_ID, mutationId);
        }
    }

    @Override
    protected void readFields(CompoundTag tag) {
        mutationId = tag.getStringOr(KEY_MUTATION_ID, "");
    }

    public String getMutationId() {
        return mutationId;
    }

    @Nullable
    public GardenMutationLayout getLayout() {
        return GardenMutationRegistry.get(mutationId);
    }

    public List<SlotContent> getIngredients() {
        List<SlotContent> snapshot = cachedIngredients;
        if (snapshot != null) return snapshot;

        snapshot = buildIngredients();
        cachedIngredients = snapshot;
        return snapshot;
    }

    public List<SlotContent> getResults() {
        List<SlotContent> snapshot = cachedResults;
        if (snapshot != null) return snapshot;

        snapshot = buildResults();
        cachedResults = snapshot;
        return snapshot;
    }

    private List<SlotContent> buildIngredients() {
        GardenMutationLayout layout = getLayout();
        if (layout == null) return List.of();

        List<SlotContent> out = new ArrayList<>();
        for (GardenMutationLayout.Cell cell : layout.grid()) {
            if (cell.type() == GardenMutationLayout.CellType.INGREDIENT && cell.itemId() != null) {
                SlotContent content = SlotRefParser.parse(cell.itemId());
                if (!content.isEmpty()) {
                    out.add(content);
                }
            }
        }
        return out.isEmpty() ? List.of() : Collections.unmodifiableList(out);
    }

    private List<SlotContent> buildResults() {
        GardenMutationLayout layout = getLayout();
        if (layout == null) return List.of();

        NeuItem item = NeuItemRegistry.get(mutationId);
        if (item == null) return List.of();

        ItemStack stack = ItemStackBuilder.build(item).copy();
        if (stack.isEmpty()) return List.of();

        return List.of(SlotContent.of(stack));
    }
}
