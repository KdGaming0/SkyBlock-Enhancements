package com.github.kd_gaming1.skyblockenhancements.feature.reminder;

import com.github.kd_gaming1.skyblockenhancements.repo.io.AtomicFileWriter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility for reading and writing JSON files safely.
 * Delegates atomic writes to {@link AtomicFileWriter}.
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

        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        }
    }

    public static void writeAtomic(Path filePath, Object data) throws IOException {
        AtomicFileWriter.writeJson(filePath, data, GSON);
    }

    private static void ensureParentFolderExists(Path filePath) throws IOException {
        Path parent = filePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }
}
