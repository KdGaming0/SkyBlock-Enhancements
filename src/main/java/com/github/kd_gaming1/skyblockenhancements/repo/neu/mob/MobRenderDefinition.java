package com.github.kd_gaming1.skyblockenhancements.repo.neu.mob;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

/**
 * Parsed {@code mobs/*.json} entry from the NEU repo, reduced to the fields that matter for
 * drop-recipe preview rendering:
 *
 * <ul>
 *   <li>{@link #entityKind} — base Minecraft entity name (e.g. {@code "Player"}, {@code "Horse"},
 *       {@code "ArmorStand"}).</li>
 *   <li>{@link #horseKind} — sub-kind when {@code entityKind == "Horse"} (e.g. {@code "skeleton"}).</li>
 *   <li>{@link #skinPath} — NEU skin path (e.g. {@code "neurepo:mobs/alligator.png"}) when
 *       {@code entityKind == "Player"}.</li>
 *   <li>{@link #helmetItemId} — NEU internal name in the helmet slot when the mob is an
 *       invisible ArmorStand with a skull-carrying helmet (Garden pests, Scatha, Wraith, …).</li>
 *   <li>{@link #rider} — nested definition for composite mobs ({@code type: "riding"}).</li>
 * </ul>
 *
 * <p>All other modifiers (age, equipment on non-skull mobs, potions, …) are ignored; rendering
 * them accurately would require replicating NEU's full modifier subsystem which is out of scope
 * for a recipe preview.
 */
public record MobRenderDefinition(
        String entityKind,
        @Nullable String horseKind,
        @Nullable String skinPath,
        @Nullable String helmetItemId,
        @Nullable MobRenderDefinition rider) {

    /** {@code true} when this mob renders as an invisible ArmorStand displaying a skull helmet. */
    public boolean isArmorStandSkull() {
        return "ArmorStand".equals(entityKind) && helmetItemId != null;
    }

    /**
     * Parses a {@code mobs/*.json} entry, descending into nested {@code modifiers} arrays.
     * Returns {@code null} for objects without an {@code "entity"} field (unusable for rendering).
     */
    @Nullable
    public static MobRenderDefinition parse(@Nullable JsonObject obj) {
        return parseRecursive(obj);
    }

    @Nullable
    private static MobRenderDefinition parseRecursive(@Nullable JsonObject obj) {
        if (obj == null) return null;

        String entity = readString(obj, "entity");
        if (entity == null) return null;

        JsonArray modifiers = readArray(obj);
        ModifierScan scan = scanModifiers(modifiers);

        return new MobRenderDefinition(
                entity, scan.horseKind, scan.skinPath, scan.helmetItemId, scan.rider);
    }

    /** Single pass over a modifiers array — captures every field we care about. */
    private static ModifierScan scanModifiers(@Nullable JsonArray modifiers) {
        ModifierScan scan = new ModifierScan();
        if (modifiers == null) return scan;

        for (var element : modifiers) {
            if (!element.isJsonObject()) continue;
            JsonObject mod = element.getAsJsonObject();
            String type = readString(mod, "type");
            if (type == null) continue;

            switch (type) {
                case "playerdata" -> scan.skinPath = firstNonNull(scan.skinPath, readString(mod, "skin"));
                case "horse"      -> scan.horseKind = firstNonNull(scan.horseKind, readString(mod, "kind"));
                case "equipment"  -> scan.helmetItemId = firstNonNull(scan.helmetItemId, readString(mod, "helmet"));
                case "riding"     -> scan.rider = firstNonNull(scan.rider, parseRecursive(mod));
                default           -> { /* ignore */ }
            }
        }
        return scan;
    }

    // ── JSON helpers ────────────────────────────────────────────────────────────

    @Nullable
    private static String readString(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : null;
    }

    @Nullable
    private static JsonArray readArray(JsonObject obj) {
        return obj.has("modifiers") && obj.get("modifiers").isJsonArray() ? obj.getAsJsonArray("modifiers") : null;
    }

    private static <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    /** Mutable accumulator used only during one {@link #scanModifiers} call. */
    private static final class ModifierScan {
        @Nullable String horseKind;
        @Nullable String skinPath;
        @Nullable String helmetItemId;
        @Nullable MobRenderDefinition rider;
    }
}