package com.github.kd_gaming1.skyblockenhancements.feature.missingenchants;

import com.github.kd_gaming1.skyblockenhancements.util.JsonLookup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnchantDataLoaderTest {
    @TempDir Path directory;

    @Test
    void publishesSavedDataBeforeDownloadingThenPublishesUpdate() throws Exception {
        Path file = directory.resolve("enchants.json");
        Files.writeString(file, MissingEnchantsTest.DATA);
        List<JsonLookup> published = new ArrayList<>();
        String update = MissingEnchantsTest.DATA.replace("[1,2,3]", "[1]");
        new EnchantDataLoader(file, () -> {
            assertEquals(1, published.size());
            return update;
        }, published::add).load();
        assertEquals(List.of(3, 1), published.stream().map(data -> data.getMaxLevel("sharpness")).toList());
        assertEquals(update, Files.readString(file));
    }

    @Test
    void invalidOrFailedUpdatesKeepSavedDataAndDiskFile() throws Exception {
        Path file = directory.resolve("enchants.json");
        Files.writeString(file, MissingEnchantsTest.DATA);
        for (String response : new String[]{null, "not json", "null", "{}",
                MissingEnchantsTest.DATA.replace("[1,2,3]", "null")}) {
            List<JsonLookup> published = new ArrayList<>();
            new EnchantDataLoader(file, () -> response, published::add).load();
            assertEquals(1, published.size());
            assertEquals(3, published.getFirst().getMaxLevel("sharpness"));
            assertEquals(MissingEnchantsTest.DATA, Files.readString(file));
        }
    }

    @Test
    void firstLaunchPublishesDownloadWithoutSavedFile() {
        List<JsonLookup> published = new ArrayList<>();
        new EnchantDataLoader(directory.resolve("new/enchants.json"),
                () -> MissingEnchantsTest.DATA, published::add).load();
        assertEquals(1, published.size());
    }

    @Test
    void unavailableDataPublishesNothing() {
        List<JsonLookup> published = new ArrayList<>();
        new EnchantDataLoader(directory.resolve("missing.json"), () -> null, published::add).load();
        assertTrue(published.isEmpty());
    }

    @Test
    void emptyPoolsAreValidAndSnapshotCollectionsAreImmutable() {
        JsonLookup data = JsonLookup.parse(MissingEnchantsTest.DATA.replace("[[\"sharpness\",\"smite\"]]", "[]"));
        assertTrue(data.getEnchantPools().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> data.getEnchants("SWORD").clear());
        assertThrows(UnsupportedOperationException.class, () -> data.getEnchantPools().add(List.of("sharpness")));
        assertEquals(List.of("Sharpness", "Smite"), new MissingEnchantResolver(data).findMissingEnchantNames("SWORD", java.util.Set.of()));
    }
}
