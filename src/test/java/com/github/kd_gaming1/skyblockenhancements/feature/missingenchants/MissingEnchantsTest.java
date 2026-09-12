package com.github.kd_gaming1.skyblockenhancements.feature.missingenchants;

import com.github.kd_gaming1.skyblockenhancements.util.JsonLookup;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MissingEnchantsTest {
    static final String DATA = """
            {"enchants":{"SWORD":["sharpness","smite"]},
             "enchant_pools":[["sharpness","smite"]],
             "enchants_xp_cost":{"sharpness":[1,2,3],"smite":[1,2,3]}}
            """;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // 26.2 binds item defaults during data loading, after bootstrap. These tests supply
        // their own components and do not need a world or the vanilla data-pack loader.
        Items.DIAMOND_SWORD.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
    }

    @Test
    void completeItemNeverInheritsPreviousWarnings() {
        MissingEnchants feature = loadedFeature();
        assertTrue(text(hover(feature, sword(0), true, false)).contains("Missing enchantments"));
        ItemStack complete = sword(3);
        for (int i = 0; i < 3; i++) {
            assertEquals(2, hover(feature, complete, true, false).size());
        }
    }

    @Test
    void nonMaxedOnlyItemGetsItsOwnWarnings() {
        MissingEnchants feature = loadedFeature();
        hover(feature, sword(0), true, false);
        ItemStack item = sword(1);
        String result = text(hover(feature, item, true, false));
        assertTrue(result.contains("Not maxed: 1"));
        assertFalse(result.contains("Missing enchantments"));
        assertEquals(result, text(hover(feature, item, true, false)));
    }

    @Test
    void settingsInvalidateTheSameItemInBothDirections() {
        MissingEnchants feature = loadedFeature();
        ItemStack item = sword(1);
        assertTrue(text(hover(feature, item, true, false)).contains("Not maxed"));
        assertEquals(2, hover(feature, item, false, false).size());
        assertTrue(text(hover(feature, item, true, false)).contains("Not maxed"));
    }

    @Test
    void firstDownloadAndReplacementRefreshTheSameItem() {
        MissingEnchants feature = new MissingEnchants(String::length);
        ItemStack item = sword(1);
        assertEquals(2, hover(feature, item, true, false).size());
        feature.publishData(JsonLookup.parse(DATA));
        assertTrue(text(hover(feature, item, true, false)).contains("Not maxed"));
        feature.publishData(JsonLookup.parse(DATA.replace("[1,2,3]", "[1]")));
        assertEquals(2, hover(feature, item, true, false).size());
    }

    @Test
    void inPlaceComponentChangeInvalidatesTheCopiedStack() {
        MissingEnchants feature = loadedFeature();
        ItemStack item = sword(1);
        hover(feature, item, true, false);
        item.set(DataComponents.CUSTOM_DATA, sword(3).get(DataComponents.CUSTOM_DATA));
        assertEquals(2, hover(feature, item, true, false).size());
    }

    @Test
    void repeatedHoversReuseComponentsAndShiftOnlyRebuildsPresentation() {
        int[] measurements = {0};
        MissingEnchants feature = new MissingEnchants(text -> {
            measurements[0]++;
            return text.length();
        });
        feature.publishData(JsonLookup.parse(DATA));
        ItemStack item = sword(1);
        List<Component> first = hover(feature, item, true, false);
        List<Component> repeated = hover(feature, item, true, false);
        assertSame(first.get(3), repeated.get(3));
        List<Component> expanded = hover(feature, item, true, true);
        assertTrue(text(expanded).contains("Sharpness I→III"));
        int count = measurements[0];
        assertTrue(count > 0);
        List<Component> expandedAgain = hover(feature, item, true, true);
        assertEquals(count, measurements[0]);
        assertSame(expanded.get(3), expandedAgain.get(3));
        assertTrue(text(hover(feature, item, true, false)).contains("Not maxed: 1"));
    }

    @Test
    void stackChangeResetsTooltipPlacementEvenWithIdenticalEnchants() {
        MissingEnchants feature = loadedFeature();
        hover(feature, sword(1), true, false);
        ItemStack second = sword(1);
        second.set(DataComponents.CUSTOM_NAME, Component.literal("Different sword"));
        List<Component> lines = new ArrayList<>(List.of(Component.literal("Name"),
                Component.literal("Extra description"), Component.literal("Sharpness I"),
                Component.literal(""), Component.literal("LEGENDARY SWORD")));
        feature.appendTooltip(second, lines, true, false);
        assertEquals("◆ Not maxed: 1 (hold Shift)", lines.get(4).getString());
    }

    private static MissingEnchants loadedFeature() {
        MissingEnchants feature = new MissingEnchants(String::length);
        feature.publishData(JsonLookup.parse(DATA));
        return feature;
    }

    private static ItemStack sword(int level) {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        CompoundTag enchants = new CompoundTag();
        if (level > 0) enchants.putInt("sharpness", level);
        CompoundTag data = new CompoundTag();
        data.put("enchantments", enchants);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }

    private static List<Component> hover(MissingEnchants feature, ItemStack stack, boolean notMaxed, boolean expanded) {
        List<Component> lines = new ArrayList<>(List.of(Component.literal("Sword"), Component.literal("LEGENDARY SWORD")));
        feature.appendTooltip(stack, lines, notMaxed, expanded);
        return lines;
    }

    private static String text(List<Component> lines) {
        return String.join("\n", lines.stream().map(Component::getString).toList());
    }
}
