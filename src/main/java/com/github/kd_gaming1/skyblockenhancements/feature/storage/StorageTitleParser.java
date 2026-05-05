package com.github.kd_gaming1.skyblockenhancements.feature.storage;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Hypixel container titles into structured page metadata.
 *
 * <p>Supported patterns:
 * <ul>
 *   <li>{@code Storage (n/m)} → {@link StoragePageType#STORAGE}</li>
 *   <li>{@code Ender Chest (n/m)} → {@link StoragePageType#ENDER_CHEST}</li>
 *   <li>{@code Backpack.*(n)} or {@code .*Backpack} → {@link StoragePageType#BACKPACK}</li>
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
            return Optional.of(new ParsedTitle(StoragePageType.STORAGE, "storage_" + page, page, plain));
        }

        m = STORAGE_SIMPLE_PATTERN.matcher(plain);
        if (m.matches()) {
            return Optional.of(new ParsedTitle(StoragePageType.STORAGE, "storage_1", 1, plain));
        }

        m = ENDER_CHEST_PATTERN.matcher(plain);
        if (m.find()) {
            int page = Integer.parseInt(m.group(1));
            return Optional.of(new ParsedTitle(StoragePageType.ENDER_CHEST, "ender_" + page, page, plain));
        }

        m = BACKPACK_NUMBERED_PATTERN.matcher(plain);
        if (m.find()) {
            int page = Integer.parseInt(m.group(1));
            return Optional.of(new ParsedTitle(StoragePageType.BACKPACK, "backpack_" + page, page, plain));
        }

        m = BACKPACK_SIMPLE_PATTERN.matcher(plain);
        if (m.matches()) {
            String id = "backpack_" + Integer.toHexString(plain.hashCode());
            return Optional.of(new ParsedTitle(StoragePageType.BACKPACK, id, 0, plain));
        }

        return Optional.empty();
    }

    private static String stripFormatting(String input) {
        // Strip all Minecraft formatting codes: § followed by any character
        return input.replaceAll("§.", "");
    }

    /**
     * Result of parsing a storage screen title.
     */
    public record ParsedTitle(
            StoragePageType type,
            String pageId,
            int pageNumber,
            String rawTitle
    ) {}
}
