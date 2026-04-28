package com.github.kd_gaming1.skyblockenhancements.feature.pricing;

import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipeUtil;
import com.github.kd_gaming1.skyblockenhancements.config.ModSettings;
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
 *
 * <p>Instance-based: created once during mod init and wired with {@link ModSettings}
 * and {@link PriceStore}.
 */
public final class PriceTooltipEnhancement {

    private static final DecimalFormat COIN_FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        COIN_FORMAT = new DecimalFormat("#,##0.#", symbols);
    }

    private final ModSettings settings;
    private final PriceStore store;

    public PriceTooltipEnhancement(ModSettings settings, PriceStore store) {
        this.settings = settings;
        this.store = store;
    }

    /** Registers the tooltip callback. Call once during mod init. */
    public void register() {
        ItemTooltipCallback.EVENT.register(this::onTooltip);
    }

    // ── Callback ────────────────────────────────────────────────────────────────

    private void onTooltip(ItemStack stack, Item.TooltipContext ctx, TooltipFlag flag, List<Component> lines) {
        if (!settings.enablePriceTooltips()) return;
        if (!com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState.isOnSkyblock()) return;
        if (!store.hasData()) return;
        if (stack.isEmpty()) return;

        String skyblockId = SkyblockRecipeUtil.extractSkyblockId(stack);
        if (skyblockId == null) return;

        Optional<PriceStore.PriceCacheEntry> cached = store.getTooltipCache(skyblockId);
        List<Component> priceLines;
        if (cached.isPresent()) {
            priceLines = cached.get().lines();
        } else {
            priceLines = buildPriceLines(skyblockId);
            if (!priceLines.isEmpty()) {
                store.putTooltipCache(skyblockId, new PriceStore.PriceCacheEntry(priceLines, System.currentTimeMillis()));
            }
        }

        if (!priceLines.isEmpty()) {
            lines.addAll(priceLines);
        }
    }

    // ── Line building ───────────────────────────────────────────────────────────

    private List<Component> buildPriceLines(String skyblockId) {
        Optional<Double> lowestBin = store.getLowestBin(skyblockId);
        Optional<BazaarPrice> bazaar = store.getBazaarPrice(skyblockId);

        if (lowestBin.isEmpty() && bazaar.isEmpty()) return List.of();

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
