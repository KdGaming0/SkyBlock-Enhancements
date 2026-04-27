package com.github.kd_gaming1.skyblockenhancements.repo.neu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

/**
 * Safe accessors for {@link JsonObject} primitives. Eliminates the repeated
 * {@code obj.has(key) && obj.get(key).isJsonPrimitive()} checks across recipe parsers.
 */
public final class JsonUtil {

    private JsonUtil() {}

    /** Returns the string value, or {@code null} if the key is missing or not a primitive. */
    @Nullable
    public static String getString(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : null;
    }

    /** Returns the string value, or {@code fallback} if the key is missing or not a primitive. */
    public static String getString(JsonObject obj, String key, String fallback) {
        String v = getString(obj, key);
        return v != null ? v : fallback;
    }

    /** Returns the int value, or {@code fallback} if the key is missing or not a primitive. */
    public static int getInt(JsonObject obj, String key, int fallback) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) return fallback;
        try {
            return obj.get(key).getAsInt();
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Returns the long value, or {@code fallback} if the key is missing or not a primitive. */
    public static long getLong(JsonObject obj, String key, long fallback) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) return fallback;
        try {
            return obj.get(key).getAsLong();
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Returns the {@link JsonArray} for {@code key}, or {@code null} if missing or not an array.
     */
    @Nullable
    public static JsonArray getArray(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonArray() ? obj.getAsJsonArray(key) : null;
    }

    /**
     * Returns a string array parsed from a JSON array of strings.
     * Returns an empty array if the key is missing or not an array.
     */
    public static String[] getStringArray(JsonObject obj, String key) {
        JsonArray arr = getArray(obj, key);
        if (arr == null) return new String[0];
        String[] out = new String[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            JsonElement el = arr.get(i);
            out[i] = el.isJsonPrimitive() ? el.getAsString() : "";
        }
        return out;
    }
}
