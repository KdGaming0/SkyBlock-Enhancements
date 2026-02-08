package com.github.kd_gaming1.skyblockenhancements.feature.reminder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Utility for reading and writing JSON files safely.
 * Uses atomic file operations to prevent data corruption during writes.
 */
public final class JsonFileUtil {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private JsonFileUtil() {}

    public static <T> T readOrCreate(Path filePath, Class<T> type, T defaultValue) throws IOException {
        ensureParentFolderExists(filePath);

        if (!Files.exists(filePath)) {
            writeAtomic(filePath, defaultValue);
            return defaultValue;
        }

        try(BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        }
    }

    public static void writeAtomic(Path filePath, Object data) throws IOException {
        ensureParentFolderExists(filePath);

        Path tempPath = filePath.resolveSibling(filePath.getFileName() + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
            GSON.toJson(data, writer);
        }

        try {
            Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void ensureParentFolderExists(Path filePath) throws IOException {
        Path parent = filePath.getParent();
        if (!Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }
}
