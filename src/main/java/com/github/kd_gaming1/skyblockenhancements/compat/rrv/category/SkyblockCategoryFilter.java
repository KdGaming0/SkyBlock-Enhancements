package com.github.kd_gaming1.skyblockenhancements.compat.rrv.category;

import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuConstantsRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.SkyblockItemCategory;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.injection.FullStackListCache;

/**
 * Tests whether an {@link ItemStack} in the RRV item list belongs to a given
 * {@link SkyblockItemCategory} and optional sub-category.
 *
 * <p>Category is resolved from the pre-populated {@link NeuItem#category} field, which
 * is set eagerly during repo parsing. No lazy resolution is needed — category is always
 * non-null for items with lore (items without lore have {@code category == null}).
 */
public final class SkyblockCategoryFilter {

    /** Fallback display-name → NeuItem index for stacks without a cached entry. */
    private static volatile Map<String, NeuItem> displayNameIndex;
    private static int indexedSize;

    private static final Pattern PET_ID_PATTERN = Pattern.compile(";\\d+$");

    private SkyblockCategoryFilter() {}

    // ── Public API ───────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the given stack belongs to the specified category.
     * Category is pre-resolved at parse time — no lazy resolution needed.
     */
    public static boolean matches(ItemStack stack, SkyblockItemCategory target) {
        if (stack.isEmpty() || target == null) return false;

        NeuItem item = resolveNeuItem(stack);
        if (item == null) return false;

        return item.category == target;
    }

    /**
     * Extended match that also checks a sub-category string (e.g. {@code "COMBAT"}).
     */
    public static boolean matches(ItemStack stack, SkyblockItemCategory target,
                                  @Nullable String subCategory) {
        if (stack.isEmpty() || target == null) return false;

        NeuItem item = resolveNeuItem(stack);
        if (item == null) return false;

        if (item.category != target) return false;
        if (subCategory == null || subCategory.isEmpty()) return true;

        return matchesSubCategory(item, target, subCategory);
    }

    /** Drops the fallback display-name index so it rebuilds on the next filter pass. */
    public static void invalidateIndex() {
        displayNameIndex = null;
    }

    // ── Sub-category resolution ─────────────────────────────────────────────────

    private static boolean matchesSubCategory(NeuItem item, SkyblockItemCategory category,
                                              String subCategory) {
        if (category == SkyblockItemCategory.PET && item.internalName != null) {
            String petId = extractPetId(item.internalName);
            if (petId != null) {
                String skillType = NeuConstantsRegistry.getPetType(petId);
                if (skillType != null) return skillType.equalsIgnoreCase(subCategory);
            }
        }

        if (item.internalName != null) {
            String wing = NeuConstantsRegistry.getMuseumWing(item.internalName);
            if (wing != null) return wing.equalsIgnoreCase(subCategory);
        }

        return false;
    }

    @Nullable
    private static String extractPetId(String internalName) {
        if (internalName == null) return null;
        int semi = internalName.indexOf(';');
        return semi >= 0 ? internalName.substring(0, semi) : internalName;
    }

    // ── Item resolution ─────────────────────────────────────────────────────────

    @Nullable
    private static NeuItem resolveNeuItem(ItemStack stack) {
        NeuItem cached = FullStackListCache.getCachedNeuItem(stack);
        if (cached != null) return cached;

        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        if (customName == null) return null;
        return ensureDisplayNameIndex().get(customName.getString());
    }

    // ── Fallback display-name index ─────────────────────────────────────────────

    private static Map<String, NeuItem> ensureDisplayNameIndex() {
        Map<String, NeuItem> idx = displayNameIndex;
        int registrySize = NeuItemRegistry.getAll().size();

        if (idx != null && registrySize == indexedSize) return idx;

        synchronized (SkyblockCategoryFilter.class) {
            if (displayNameIndex != null && registrySize == indexedSize) {
                return displayNameIndex;
            }

            Map<String, NeuItem> newIndex = new HashMap<>(registrySize);
            for (NeuItem item : NeuItemRegistry.getAll().values()) {
                if (item.displayName != null) {
                    newIndex.put(item.displayName, item);
                }
            }
            indexedSize = registrySize;
            displayNameIndex = newIndex;
            return newIndex;
        }
    }
}