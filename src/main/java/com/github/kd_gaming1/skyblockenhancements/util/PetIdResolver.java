package com.github.kd_gaming1.skyblockenhancements.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * Extracts SkyBlock internal IDs from pet items.
 *
 * <p>Pets store their actual type and tier in a JSON string called {@code petInfo},
 * rather than in the {@code id} field directly. This resolver parses that JSON and
 * reconstructs the NEU-style ID ({@code TYPE;TIER_NUMBER}).
 *
 * <p>Supported formats:
 * <ul>
 *   <li>NEU overlay: {@code CustomData { id: "PET", petInfo: "{...}" }}</li>
 *   <li>Hypixel server: {@code CustomData { ExtraAttributes { id: "PET", petInfo: "{...}" } }}</li>
 * </ul>
 */
public final class PetIdResolver {

    private PetIdResolver() {}

    /**
     * Attempts to resolve a pet ID from the given NBT tag.
     *
     * <p>Checks both the direct tag (NEU format) and the {@code ExtraAttributes}
     * sub-compound (Hypixel server format) for a {@code petInfo} JSON string.
     *
     * @param tag the {@code CustomData} compound to inspect
     * @return {@code TYPE;TIER_NUMBER} if a valid petInfo block is found,
     *         otherwise {@code null}
     */
    @Nullable
    public static String resolveFromTag(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return null;

        // Try NEU format: petInfo is a direct sibling of id
        String petInfo = tag.getStringOr("petInfo", "");
        if (!petInfo.isEmpty()) {
            return parsePetInfo(petInfo);
        }

        // Try Hypixel format: petInfo is inside ExtraAttributes
        var extraOpt = tag.getCompound("ExtraAttributes");
        if (extraOpt.isPresent()) {
            CompoundTag extra = extraOpt.get();
            petInfo = extra.getStringOr("petInfo", "");
            if (!petInfo.isEmpty()) {
                return parsePetInfo(petInfo);
            }
        }

        return null;
    }

    /**
     * Parses the {@code petInfo} JSON string and returns a NEU-style pet ID.
     *
     * <p>Example JSON: {@code {"type":"PIGMAN","tier":"LEGENDARY","exp":0.0}}
     * → returns {@code "PIGMAN;4"}
     *
     * @param petInfoJson the raw JSON string from NBT
     * @return {@code TYPE;TIER_NUMBER} or {@code null} if parsing fails
     */
    @Nullable
    private static String parsePetInfo(String petInfoJson) {
        if (petInfoJson == null || petInfoJson.isEmpty()) return null;

        try {
            JsonObject obj = JsonParser.parseString(petInfoJson).getAsJsonObject();
            if (!obj.has("type") || !obj.has("tier")) return null;

            String type = obj.get("type").getAsString();
            String tier = obj.get("tier").getAsString();
            int tierNumber = tierToNumber(tier);
            if (tierNumber < 0) return null;

            return type + ";" + tierNumber;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Maps a SkyBlock rarity/tier string to its numeric index.
     *
     * @return 0–5 for recognised tiers, -1 for unknown
     */
    private static int tierToNumber(String tier) {
        return switch (tier) {
            case "COMMON" -> 0;
            case "UNCOMMON" -> 1;
            case "RARE" -> 2;
            case "EPIC" -> 3;
            case "LEGENDARY" -> 4;
            case "MYTHIC" -> 5;
            default -> -1;
        };
    }
}
