package com.github.kd_gaming1.skyblockenhancements.feature.mining.calc;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class BreakTimeCalculatorTest {

    @ParameterizedTest
    @CsvSource({
            // blockStrength, miningSpeed, expectedTicks
            "8,    60,   4",    // Netherrack at 4-tick threshold
            "8,    100,  4",    // Netherrack above threshold (floor)
            "8,    240,  4",    // Netherrack at instant (floor)
            "15,   113,  4",    // Stone at 4-tick threshold
            "15,   225,  4",    // Stone above threshold
            "50,   375,  4",    // Hard Stone at 4-tick threshold
            "50,   750,  4",    // Hard Stone above threshold
            "500,  3750, 4",    // Obsidian at 4-tick threshold
            "30,   1,    900",  // Ore at minimum speed
            "30,   225,  4",    // Ore at 4-tick threshold
            "30,   450,  4",    // Ore above threshold (still 4)
            "30,   1800, 4",    // Ore at instant (still 4 due to floor)
            "2300, 17250, 4",   // Ruby at 4-tick threshold
            "3000, 22500, 4",   // Jade at 4-tick threshold
            "6000, 45000, 4",   // Glacite at 4-tick threshold
    })
    void testBreakTicks(double strength, int speed, int expected) {
        assertEquals(expected, BreakTimeCalculator.calculateBreakTicks(strength, speed));
    }

    @ParameterizedTest
    @CsvSource({
            "100, 0,  2147483647",
            "100, -1, 2147483647",
    })
    void testBreakTicksEdgeCases(double strength, int speed, int expected) {
        assertEquals(expected, BreakTimeCalculator.calculateBreakTicks(strength, speed));
    }

    @ParameterizedTest
    @CsvSource({
            "0,   20,  0,   20.0, 0.0",
            "10,  20,  0,   20.0, 0.5",
            "20,  20,  0,   20.0, 1.0",
            "9,   20,  100, 20.0, 0.5",    // 100ms ping = 2 tick offset, adjusted = 18
            "18,  20,  100, 20.0, 1.0",    // Reached adjusted break → capped at 1.0
            "20,  20,  100, 20.0, 1.0",    // Past adjusted break → capped at 1.0
    })
    void testProgressPercent(int elapsed, int total, double ping, double tps, double expected) {
        double actual = BreakTimeCalculator.calculateProgressPercent(elapsed, total, ping, tps);
        assertEquals(expected, actual, 0.01);
    }

    @ParameterizedTest
    @CsvSource({
            "30,  false, 800,  false",  // Stone, not ore, below instant (30*30=900)
            "30,  false, 900,  true",   // Stone at exact instant threshold
            "30,  true,  1700, false",  // Ore, below instant (30*60=1800)
            "30,  true,  1800, true",   // Ore at exact instant threshold
            "30,  true,  2000, true",   // Ore, above instant
    })
    void testCanInstantMine(double strength, boolean isOre, int speed, boolean expected) {
        assertEquals(expected, BreakTimeCalculator.canInstantMine(strength, isOre, speed));
    }
}
