package com.github.kd_gaming1.skyblockenhancements.feature.storage;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Hypixel container titles into structured page metadata.
 *
 * <p>Supported patterns:
 * <ul>
 *   <li>{@code Storage (n/m)} → overview / Ender Chest page n</li>
 *   <li>{@code Ender Chest (n/m)} → Ender Chest page n</li>
 *   <li>{@code Backpack.*(n)} or {@code .*Backpack} → Backpack page n</li>
 * </ul>
 */
public final class StorageTitleParser {

    private static final Pattern STORAGE_PATTERN = Pattern.compile(".*[Ss]torage.*\\((\\d+)/(\\d+)\\)");
    private static final Pattern STORAGE_SIMPLE_PATTERN = Pattern.compile("^[Ss]torage$");
    private static final Pattern ENDER_CHEST_PATTERN = Pattern.compile(".*[Ee]nder [Cc]hest.*\\((\\d+)/(\\d+)\\)");
    private static final Pattern BACKPACK_NUMBERED_PATTERN = Pattern.compile(".*[Bb]ackpack.*\\((\\d+)\\)");
    private static final Pattern BACKPACK_SIMPLE_PATTERN = Pattern.compile(".*[Bb]ackpack.*");

    private StorageTitleParser() {}

    /**
     * Attempts to classify a screen title as a storage page.
     *
     * @param title the raw title text (strip formatting codes beforehand)
     * @return parsed metadata, or empty if the title does not match any known pattern
     */
    public static Optional<ParsedTitle> parse(String title) {
        String plain = stripFormatting(title);

        Matcher m = STORAGE_PATTERN.matcher(plain);
        if (m.find()) {
            int page = Integer.parseInt(m.group(1));
            StoragePageSlot slot = StoragePageSlot.ofEnderChest(page);
            return Optional.of(new ParsedTitle(slot, plain));
        }

        m = STORAGE_SIMPLE_PATTERN.matcher(plain);
        if (m.matches()) {
            // Storage hub overview — no specific page
            return Optional.of(new ParsedTitle(null, plain));
        }

        m = ENDER_CHEST_PATTERN.matcher(plain);
        if (m.find()) {
            int page = Integer.parseInt(m.group(1));
            StoragePageSlot slot = StoragePageSlot.ofEnderChest(page);
            return Optional.of(new ParsedTitle(slot, plain));
        }

        m = BACKPACK_NUMBERED_PATTERN.matcher(plain);
        if (m.find()) {
            int page = Integer.parseInt(m.group(1));
            StoragePageSlot slot = StoragePageSlot.ofBackpack(page);
            return Optional.of(new ParsedTitle(slot, plain));
        }

        m = BACKPACK_SIMPLE_PATTERN.matcher(plain);
        if (m.matches()) {
            // Simple backpack without number — we can't determine the slot reliably.
            // Return null slot so caller can decide (e.g., skip or hash).
            return Optional.of(new ParsedTitle(null, plain));
        }

        return Optional.empty();
    }

    private static String stripFormatting(String input) {
        return input.replaceAll("§.", "");
    }

    /**
     * Result of parsing a storage screen title.
     *
     * @param slot the resolved page slot, or null for overview / unknown
     * @param rawTitle the cleaned title text
     */
    public record ParsedTitle(
            StoragePageSlot slot,
            String rawTitle
    ) {
        public boolean isOverview() {
            return slot == null && "Storage".equalsIgnoreCase(rawTitle);
        }
    }
}
