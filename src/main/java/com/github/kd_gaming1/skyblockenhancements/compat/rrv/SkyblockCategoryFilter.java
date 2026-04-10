package com.github.kd_gaming1.skyblockenhancements.compat.rrv;

import com.github.kd_gaming1.skyblockenhancements.repo.NeuConstantsRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.NeuItemRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.SkyblockItemCategory;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/**
 * Tests whether an {@link ItemStack} in the RRV item list belongs to a given
 * {@link SkyblockItemCategory} and optional sub-category. Resolves the stack back to its
 * {@link NeuItem} via the internal name stored in {@link DataComponents#CUSTOM_DATA}.
 *
 * <p>Resolution by internal name (set in {@link com.github.kd_gaming1.skyblockenhancements.repo.ItemStackBuilder})
 * is collision-free — unlike display names, internal names are unique per item.
 *
 * <p>Category resolution is cached on each {@link NeuItem} as a transient field, so repeated
 * lookups for the same item are constant-time after the first pass.
 */
public final class SkyblockCategoryFilter {

    /**
     * Fallback display-name → NeuItem index for stacks without CustomData (shouldn't happen
     * in normal operation, but defensively handles edge cases). Rebuilt lazily.
     */
    private static volatile Map<String, NeuItem> displayNameIndex;
    private static int indexedSize;

    /** Pre-compiled pattern for extracting pet ID from internal names like {@code "BEE;4"}. */
    private static final Pattern PET_ID_PATTERN = Pattern.compile(";\\d+$");

    private SkyblockCategoryFilter() {}

    /**
     * Returns {@code true} if the given stack's underlying {@link NeuItem} belongs to the
     * specified category. Returns {@code false} for stacks that can't be resolved to a NeuItem
     * or whose NeuItem has no assigned category.
     */
    public static boolean matches(ItemStack stack, SkyblockItemCategory target) {
        if (stack.isEmpty() || target == null) return false;

        NeuItem item = resolveNeuItem(stack);
        if (item == null) return false;

        if (item.category == null) {
            item.category = SkyblockItemCategory.fromNeuItem(item);
        }
        return item.category == target;
    }

    /**
     * Extended match that also checks a sub-category string (e.g. {@code "COMBAT"}, {@code "MINING"}).
     * Sub-categories are resolved from:
     * <ul>
     *   <li>Pet skill types (from {@code pets.json}) — for PET category</li>
     *   <li>Museum wing names (from {@code museum.json}) — for any category</li>
     * </ul>
     *
     * @param stack       the item stack to test
     * @param target      the primary category
     * @param subCategory the sub-category string (case-insensitive), or {@code null} to skip
     * @return {@code true} if both the category and sub-category match
     */
    public static boolean matches(ItemStack stack, SkyblockItemCategory target,
                                  @Nullable String subCategory) {
        if (!matches(stack, target)) return false;
        if (subCategory == null || subCategory.isEmpty()) return true;

        NeuItem item = resolveNeuItem(stack);
        if (item == null) return false;

        String sub = subCategory.toUpperCase(Locale.ROOT);
        return matchesSubCategory(item, target, sub);
    }

    /** Drops the fallback display-name index so it's rebuilt on the next filter pass. */
    public static void invalidateIndex() {
        displayNameIndex = null;
    }

    // ── Sub-category resolution ─────────────────────────────────────────────────

    /**
     * Checks sub-category membership using constants data. For PET items, looks up the
     * pet's skill type from {@code pets.json}. For all items, falls back to museum wing
     * from {@code museum.json}.
     */
    private static boolean matchesSubCategory(NeuItem item, SkyblockItemCategory category,
                                              String subCategory) {
        // Pet skill type check (e.g. %PET/COMBAT → check pets.json for COMBAT skill)
        if (category == SkyblockItemCategory.PET && item.internalName != null) {
            String petId = extractPetId(item.internalName);
            if (petId != null) {
                String skillType = NeuConstantsRegistry.getPetType(petId);
                if (skillType != null) {
                    return skillType.equalsIgnoreCase(subCategory);
                }
            }
        }

        // Museum wing check (e.g. %ARMOR/COMBAT → check museum.json for "combat" wing)
        if (item.internalName != null) {
            String wing = NeuConstantsRegistry.getMuseumWing(item.internalName);
            if (wing != null) {
                return wing.equalsIgnoreCase(subCategory);
            }
        }

        return false;
    }

    /**
     * Extracts the pet base ID from an internal name like {@code "BEE;4"} → {@code "BEE"}.
     * Returns the name unchanged if no semicolon is present.
     */
    @Nullable
    private static String extractPetId(String internalName) {
        if (internalName == null) return null;
        int semi = internalName.indexOf(';');
        return semi >= 0 ? internalName.substring(0, semi) : internalName;
    }

    // ── Item resolution ─────────────────────────────────────────────────────────

    /**
     * Resolves an {@link ItemStack} back to its {@link NeuItem} source. Uses the internal
     * name stored in {@link DataComponents#CUSTOM_DATA} (set by {@code ItemStackBuilder})
     * for collision-free resolution. Falls back to display-name index for stacks without
     * custom data.
     */
    @Nullable
    private static NeuItem resolveNeuItem(ItemStack stack) {
        // Primary: resolve via internal name in CustomData (collision-free)
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            String internalName = tag.getStringOr("id", "");
            if (!internalName.isEmpty()) {
                return NeuItemRegistry.get(internalName);
            }
        }

        // Fallback: display-name index (for stacks without CustomData, if any)
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