package com.github.kd_gaming1.skyblockenhancements.feature.katreminder;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the persistence load behavior of Kat reminders.
 * Ensures malformed JSON is recovered safely (backup + reset + notice flag)
 * and valid JSON loads reminders without triggering recovery.
 */
class KatUpgradeReminderManagerTest {
    @Test
    void loadCreatesBackupAndResetsFileWhenJsonIsBroken() throws IOException, ReflectiveOperationException {
        Path tempDir = Files.createTempDirectory("kat-reminder-test");
        Path storagePath = tempDir.resolve("kat_reminders.json");
        String brokenJson = "\"legacy-string-format\"";
        Files.writeString(storagePath, brokenJson, StandardCharsets.UTF_8);

        KatUpgradeReminderManager manager = new KatUpgradeReminderManager(storagePath, () -> true);
        manager.load();

        Path backupPath = storagePath.resolveSibling("kat_reminders.json.broken");
        assertTrue(Files.exists(backupPath), "Broken file backup should exist");
        assertEquals(brokenJson, Files.readString(backupPath, StandardCharsets.UTF_8));
        assertTrue(manager.getActiveReminders().isEmpty(), "Recovered reminders should be empty");

        String freshJson = Files.readString(storagePath, StandardCharsets.UTF_8);
        JsonObject jsonObject = JsonParser.parseString(freshJson).getAsJsonObject();
        assertTrue(jsonObject.has("reminders"), "Fresh file should contain reminders key");
        assertTrue(jsonObject.getAsJsonArray("reminders").isEmpty(), "Fresh reminders array should be empty");

        Field pendingNoticeField = KatUpgradeReminderManager.class.getDeclaredField("pendingBrokenFileNotice");
        pendingNoticeField.setAccessible(true);
        assertTrue((boolean) pendingNoticeField.get(manager), "Broken file notice should be scheduled");
    }

    @Test
    void loadReadsValidFileWithoutBackupOrBrokenNotice() throws IOException, ReflectiveOperationException {
        Path tempDir = Files.createTempDirectory("kat-reminder-test-valid");
        Path storagePath = tempDir.resolve("kat_reminders.json");
        String validJson = """
                {
                  "reminders": [
                    {
                      "pet": "Tiger",
                      "rarity": "LEGENDARY",
                      "readyAtMs": 123456789
                    }
                  ]
                }
                """;
        Files.writeString(storagePath, validJson, StandardCharsets.UTF_8);

        KatUpgradeReminderManager manager = new KatUpgradeReminderManager(storagePath, () -> true);
        manager.load();

        Path backupPath = storagePath.resolveSibling("kat_reminders.json.broken");
        assertFalse(Files.exists(backupPath), "No backup should be created for valid JSON");
        assertEquals(1, manager.getActiveReminders().size(), "Reminder should load from valid file");
        assertEquals("Tiger", manager.getActiveReminders().get(0).pet);
        assertEquals("LEGENDARY", manager.getActiveReminders().get(0).rarity);
        assertEquals(123456789L, manager.getActiveReminders().get(0).readyAtMs);

        Field pendingNoticeField = KatUpgradeReminderManager.class.getDeclaredField("pendingBrokenFileNotice");
        pendingNoticeField.setAccessible(true);
        assertFalse((boolean) pendingNoticeField.get(manager), "Broken file notice should stay disabled");
    }
}
