package com.github.kd_gaming1.skyblockenhancements.compat.rrv.util;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import java.net.URI;
import java.util.List;
import com.github.kd_gaming1.skyblockenhancements.mixin.rrv.accessor.RecipeViewMenuAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.injection.FullStackListCache;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.RecipeTagCodec;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.render.RecipeLayoutConstants;

public final class SkyblockRecipeUtil {

    private SkyblockRecipeUtil() {}

    // ── Item matching ────────────────────────────────────────────────────────────

    public static boolean matchesAny(ItemStack query, List<SlotContent> slots) {
        String queryId = extractSkyblockId(query);
        if (queryId == null) return false;

        for (SlotContent slot : slots) {
            for (ItemStack candidate : slot.getValidContents()) {
                if (queryId.equals(extractSkyblockId(candidate))) return true;
            }
        }
        return false;
    }

    public static boolean matchesAnyOrFamily(ItemStack query, List<SlotContent> slots) {
        String queryId = extractSkyblockId(query);
        if (queryId == null) return false;

        for (SlotContent slot : slots) {
            for (ItemStack candidate : slot.getValidContents()) {
                String candidateId = extractSkyblockId(candidate);
                if (candidateId == null) continue;
                if (queryId.equals(candidateId)) return true;
                if (ItemFamilyHelper.isFamilyMember(queryId, candidateId)) return true;
            }
        }
        return false;
    }

    public static String extractSkyblockId(ItemStack stack) {
        return FullStackListCache.getCachedId(stack);
    }

    // ── Tier extraction ─────────────────────────────────────────────────────────

    public static int extractTierFromId(String internalId) {
        if (internalId == null || internalId.isEmpty()) return 0;

        int lastUnderscore = internalId.lastIndexOf('_');
        if (lastUnderscore < 0 || lastUnderscore >= internalId.length() - 1) return 0;

        try {
            return Integer.parseInt(internalId.substring(lastUnderscore + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static int extractTierFromResults(List<SlotContent> results) {
        if (results == null || results.isEmpty()) return 0;

        for (SlotContent slot : results) {
            for (ItemStack stack : slot.getValidContents()) {
                int tier = extractTierFromId(extractSkyblockId(stack));
                if (tier > 0) return tier;
            }
        }
        return 0;
    }

    // ── Number formatting ────────────────────────────────────────────────────────

    /**
     * Formats a number with k/M/B suffixes.
     * Exact multiples omit the decimal: 1000 → "1k", 2000000 → "2M".
     * Non-exact values use one decimal: 1500 → "1.5k", 1234 → "1.2k".
     */
    public static String formatNumber(long value) {
        if (value < 0) return "-" + formatNumber(-value);
        if (value >= 1_000_000_000L) return compact(value, 1_000_000_000L, "B");
        if (value >= 1_000_000L)     return compact(value, 1_000_000L,     "M");
        if (value >= 1_000L)         return compact(value, 1_000L,         "k");
        return Long.toString(value);
    }

    private static String compact(long value, long divisor, String suffix) {
        return ((value % divisor == 0)
                ? Long.toString(value / divisor)
                : String.format("%.1f", (double) value / divisor))
                + suffix;
    }

    // ── Duration formatting ─────────────────────────────────────────────────────

    /** Formats a second count as {@code "1h 30m"} / {@code "5m"} / {@code "45s"}. */
    public static String formatDuration(int seconds) {
        if (seconds >= 3600) {
            int h = seconds / 3600;
            int m = (seconds % 3600) / 60;
            return m > 0 ? h + "h " + m + "m" : h + "h";
        }
        if (seconds >= 60) return (seconds / 60) + "m";
        return seconds + "s";
    }

    // ── Text formatting ──────────────────────────────────────────────────────────

    /** Returns {@code Component.literal("§7" + text)}. */
    public static Component gray(String text) {
        return Component.literal("§7" + text);
    }

    /** Returns {@code Component.literal("§6" + text)}. */
    public static Component gold(String text) {
        return Component.literal("§6" + text);
    }

    /** Returns {@code Component.literal("§8" + text)}. */
    public static Component darkGray(String text) {
        return Component.literal("§8" + text);
    }

    /** Convenience: {@code Minecraft.getInstance().font}. */
    public static Font font() {
        return Minecraft.getInstance().font;
    }

    /**
     * Truncates {@code text} to {@code maxWidth} pixels, appending {@code "…"} when clipped.
     * Operates on plain strings; for formatted sequences see {@link #ellipsize(Font, FormattedCharSequence, int)}.
     */
    public static Component ellipsize(Font font, String text, int maxWidth) {
        Component full = Component.literal(text);
        if (font.width(full) <= maxWidth) return full;

        String ellipsis = "…";
        int ellipsisWidth = font.width(ellipsis);
        int available = Math.max(0, maxWidth - ellipsisWidth);
        String trimmed = font.substrByWidth(full, available).getString();
        return Component.literal(trimmed + ellipsis);
    }

    /**
     * Truncates a {@link FormattedCharSequence} to {@code maxWidth}, appending {@code "…"}.
     */
    public static net.minecraft.util.FormattedCharSequence ellipsize(Font font, net.minecraft.util.FormattedCharSequence line, int maxWidth) {
        String ellipsis = "…";
        int ellipsisWidth = font.width(ellipsis);
        int available = Math.max(0, maxWidth - ellipsisWidth);
        net.minecraft.util.FormattedCharSequence head = net.minecraft.util.FormattedCharSequence.composite(
                (net.minecraft.util.FormattedCharSequence) font.substrByWidth((net.minecraft.network.chat.FormattedText) line, available));
        return net.minecraft.util.FormattedCharSequence.composite(head,
                net.minecraft.util.FormattedCharSequence.forward(ellipsis, net.minecraft.network.chat.Style.EMPTY));
    }

    // ── Command helpers ──────────────────────────────────────────────────────────

    /** Sends a client-side command after null-checking the player and connection. */
    public static void sendCommand(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.getConnection() != null) {
            mc.getConnection().sendCommand(command);
        }
    }

    // ── Wiki button ──────────────────────────────────────────────────────────────

    /**
     * Adds a Wiki button to the screen. Returns the button, or {@code null} if
     * {@code wikiUrls} is empty — callers use this as the sentinel reference.
     */
    public static Button addWikiButton(
            RecipeViewScreen screen, String[] wikiUrls, int btnX, int btnY) {
        if (wikiUrls == null || wikiUrls.length == 0) return null;

        String url = wikiUrls.length > 1 ? wikiUrls[1] : wikiUrls[0];
        Button btn = Button.builder(Component.literal("Wiki"), b -> openUri(url))
                .pos(btnX, btnY)
                .size(RecipeLayoutConstants.WIKI_BUTTON_WIDTH, RecipeLayoutConstants.WIKI_BUTTON_HEIGHT)
                .build();
        screen.addRecipeWidget(btn);
        return btn;
    }

    public static void openUri(String uri) {
        try {
            Util.getPlatform().openUri(URI.create(uri));
        } catch (Exception ignored) {
        }
    }

    // ── NBT helpers for wiki URLs ────────────────────────────────────────────────

    public static void writeWikiUrls(CompoundTag tag, String[] urls) {
        if (urls == null || urls.length == 0) return;
        ListTag list = new ListTag();
        for (String url : urls) list.add(StringTag.valueOf(url));
        tag.put(RecipeTagCodec.KEY_WIKI_URLS, list);
    }

    public static String[] readWikiUrls(CompoundTag tag) {
        ListTag list = tag.getListOrEmpty(RecipeTagCodec.KEY_WIKI_URLS);
        if (list.isEmpty()) return new String[0];
        String[] urls = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            urls[i] = String.valueOf(list.getString(i));
        }
        return urls;
    }

    public static String[] sanitizeWikiUrls(String[] urls) {
        if (urls == null) return new String[0];
        int validCount = 0;
        for (String url : urls) if (url != null && !url.isEmpty()) validCount++;
        if (validCount == urls.length) return urls;
        String[] clean = new String[validCount];
        int idx = 0;
        for (String url : urls) if (url != null && !url.isEmpty()) clean[idx++] = url;
        return clean;
    }

    // ── Page seek ────────────────────────────────────────────────────────────────

    public static void seekToMatchingPage(RecipeViewMenu menu, String targetId) {
        if (targetId == null || !menu.hasNextRecipe()) return;

        int maxPage = menu.getMaxPageIndex();
        for (int page = 0; page <= maxPage; page++) {
            List<ReliableClientRecipe> display =
                    ((RecipeViewMenuAccessor) menu).sbe$getCurrentDisplay();
            if (displayContainsResult(display, targetId)) return;
            if (page < maxPage) menu.nextPage();
        }

        while (menu.hasPrevRecipe()) menu.prevPage();
    }

    private static boolean displayContainsResult(
            List<ReliableClientRecipe> display, String targetId) {
        for (ReliableClientRecipe recipe : display) {
            for (SlotContent result : recipe.getResults()) {
                for (ItemStack candidate : result.getValidContents()) {
                    if (targetId.equals(extractSkyblockId(candidate))) return true;
                }
            }
        }
        return false;
    }
}