package com.github.kd_gaming1.skyblockenhancements.feature.pricing;

import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SkyblockRecipeUtil;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.pricing.PriceDataFetcher.BazaarPrice;
import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Appends Auction House lowest-BIN and Bazaar price lines to Skyblock item tooltips.
 */
public final class PriceTooltipEnhancement {

    private static final DecimalFormat COIN_FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        COIN_FORMAT = new DecimalFormat("#,##0.#", symbols);
    }

    // ── Identity cache ──────────────────────────────────────────────────────────

    private static ItemStack lastStack;
    private static List<Component> cachedLines = List.of();

    private PriceTooltipEnhancement() {}

    /** Registers the tooltip callback. Call once during mod init. */
    public static void init() {
        ItemTooltipCallback.EVENT.register(PriceTooltipEnhancement::onTooltip);
    }

    // ── Callback ────────────────────────────────────────────────────────────────

    private static void onTooltip(ItemStack stack, Item.TooltipContext ctx, TooltipFlag flag, List<Component> lines) {
        if (!SkyblockEnhancementsConfig.enablePriceTooltips) return;
        if (!HypixelLocationState.isOnSkyblock()) return;
        if (!PriceDataFetcher.hasData()) return;
        if (stack.isEmpty()) return;

        // Identity cache: same object reference → reuse previous result.
        if (stack == lastStack) {
            if (!cachedLines.isEmpty()) {
                lines.addAll(cachedLines);
            }
            return;
        }

        lastStack = stack;
        cachedLines = buildPriceLines(stack);

        if (!cachedLines.isEmpty()) {
            lines.addAll(cachedLines);
        }
    }

    // ── Line building ───────────────────────────────────────────────────────────

    private static List<Component> buildPriceLines(ItemStack stack) {
        String skyblockId = SkyblockRecipeUtil.extractSkyblockId(stack);
        if (skyblockId == null) return List.of();

        Optional<Double> lowestBin = PriceDataFetcher.getLowestBin(skyblockId);
        Optional<BazaarPrice> bazaar = PriceDataFetcher.getBazaarPrice(skyblockId);

        if (lowestBin.isEmpty() && bazaar.isEmpty()) return List.of();

        // Build lines: blank separator, then each available price.
        List<Component> builder = new ArrayList<>(4);
        builder.add(Component.empty());

        lowestBin.ifPresent(aDouble -> builder.add(priceLine("AH Lowest BIN", aDouble)));

        if (bazaar.isPresent()) {
            BazaarPrice bz = bazaar.get();
            if (bz.buyPrice() > 0) {
                builder.add(priceLine("BZ Buy Price", bz.buyPrice()));
            }
            if (bz.sellPrice() > 0) {
                builder.add(priceLine("BZ Sell Price", bz.sellPrice()));
            }
        }

        // If only the separator was added and nothing else, return empty.
        return builder.size() > 1 ? List.copyOf(builder) : List.of();
    }

    /**
     * Formats a single price line: {@code §6<label>: §e<formatted coins> coins}.
     */
    private static MutableComponent priceLine(String label, double price) {
        return Component.literal(label + ": ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(formatCoins(price) + " coins").withStyle(ChatFormatting.YELLOW));
    }

    /**
     * Formats a coin value for display. Uses suffixed notation for large values
     * (e.g. 1.2M, 3.5B) and comma-separated notation for smaller ones.
     */
    static String formatCoins(double value) {
        if (value >= 1_000_000_000) {
            return String.format(Locale.US, "%.1fB", value / 1_000_000_000);
        }
        if (value >= 1_000_000) {
            return String.format(Locale.US, "%.1fM", value / 1_000_000);
        }
        return COIN_FORMAT.format(value);
    }
}