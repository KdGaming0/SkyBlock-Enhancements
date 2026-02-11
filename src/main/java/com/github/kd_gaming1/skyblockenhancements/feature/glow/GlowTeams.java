package com.github.kd_gaming1.skyblockenhancements.feature.glow;

import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.Map;

/**
 * Manages scoreboard teams used to apply glow colors for item rarities.
 */
public final class GlowTeams {
    private GlowTeams() {}

    private static final Map<String, ChatFormatting> RARITY_COLORS = Map.ofEntries(
            Map.entry("COMMON", ChatFormatting.WHITE),
            Map.entry("UNCOMMON", ChatFormatting.GREEN),
            Map.entry("RARE", ChatFormatting.BLUE),
            Map.entry("EPIC", ChatFormatting.DARK_PURPLE),
            Map.entry("LEGENDARY", ChatFormatting.GOLD),
            Map.entry("MYTHIC", ChatFormatting.LIGHT_PURPLE),
            Map.entry("DIVINE", ChatFormatting.AQUA),
            Map.entry("SPECIAL", ChatFormatting.RED),
            Map.entry("VERY SPECIAL", ChatFormatting.RED),
            Map.entry("ULTIMATE", ChatFormatting.DARK_RED),
            Map.entry("ADMIN", ChatFormatting.DARK_RED)
    );


    public static String teamNameForRarity(String rarity) {
        return "sbe_glow_" + rarity.toLowerCase().replace(" ", "_");
    }

    public static PlayerTeam ensureTeam(Level level, String rarity) {
        Scoreboard scoreboard = level.getScoreboard();
        String name = teamNameForRarity(rarity);

        PlayerTeam team = scoreboard.getPlayerTeam(name);
        if (team != null) return team;

        team = scoreboard.addPlayerTeam(name);

        ChatFormatting color = RARITY_COLORS.getOrDefault(rarity, ChatFormatting.WHITE);
        team.setColor(color);
        team.setSeeFriendlyInvisibles(true);
        return team;
    }
}
