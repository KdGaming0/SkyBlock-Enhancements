package com.github.kd_gaming1.skyblockenhancements.repo.io;

import com.google.gson.Gson;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Writes files atomically to prevent corruption if the process crashes mid-write.
 *
 * <p>Implementation: write to {@code target + ".tmp"}, then
 * {@code Files.move(tmp, target, ATOMIC_MOVE, REPLACE_EXISTING)}.
 * Falls back to a non-atomic move on filesystems that do not support atomic moves.
 */
public final class AtomicFileWriter {

    private AtomicFileWriter() {}

    /**
     * Serialises {@code data} as JSON and writes it atomically to {@code target}.
     *
     * @param target the final file path
     * @param data   the object to serialise
     * @param gson   the Gson instance to use
     * @throws IOException if the write or atomic move fails
     */
    public static void writeJson(Path target, Object data, Gson gson) throws IOException {
        ensureParentFolderExists(target);

        Path tempPath = target.resolveSibling(target.getFileName() + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
            gson.toJson(data, writer);
        }

        moveAtomically(tempPath, target);
    }

    /**
     * Writes a string atomically to {@code target} using UTF-8.
     *
     * @param target the final file path
     * @param data   the string to write
     * @throws IOException if the write or atomic move fails
     */
    public static void writeString(Path target, String data) throws IOException {
        ensureParentFolderExists(target);

        Path tempPath = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tempPath, data, java.nio.charset.StandardCharsets.UTF_8);

        moveAtomically(tempPath, target);
    }

    /**
     * Writes raw bytes atomically to {@code target}.
     *
     * @param target the final file path
     * @param data   the bytes to write
     * @throws IOException if the write or atomic move fails
     */
    public static void writeBytes(Path target, byte[] data) throws IOException {
        ensureParentFolderExists(target);

        Path tempPath = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tempPath, data);

        moveAtomically(tempPath, target);
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void ensureParentFolderExists(Path filePath) throws IOException {
        Path parent = filePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }
}
