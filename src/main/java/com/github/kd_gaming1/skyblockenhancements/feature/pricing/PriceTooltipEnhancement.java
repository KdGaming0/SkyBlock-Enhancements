package com.github.kd_gaming1.skyblockenhancements.feature.pricing;

import com.github.kd_gaming1.skyblockenhancements.config.ModSettings;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import com.github.kd_gaming1.skyblockenhancements.util.LogicalStackSize;
import com.github.kd_gaming1.skyblockenhancements.util.SkyblockItemUtil;
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
 * <p>Supports two live-modifier keybinds that multiply displayed prices by the
 * hovered item's max stack size or current stack count. When no modifier is held,
 * small gray hint lines display the currently-bound keys so the feature is discoverable.
 * A ticker-text option makes the coin value bold for better visibility, and an
 * optional formatting toggle can show the raw full number instead of rounded shorthand.
 *
 * <p>When the API is down or data hasn't loaded yet, shows "Can't load data"
 * instead of hiding the price section entirely.
 *
 * <p>Instance-based: created once during mod init and wired with {@link ModSettings}
 * and {@link PriceStore}.
 */
public final class PriceTooltipEnhancement {

    private static final DecimalFormat COIN_FORMAT;
    private static final int FULL_STACK_SIZE = 64;

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
        if (!HypixelLocationState.isOnSkyblock()) return;
        if (stack.isEmpty()) return;

        String skyblockId = SkyblockItemUtil.getPriceLookupId(stack);
        if (skyblockId == null) return;

        // Pass existing tooltip lines so Bazaar order amounts can be parsed
        long logicalSize = LogicalStackSize.getLogicalStackSize(stack, lines);
        int multiplier = computeMultiplier(stack, logicalSize);
        boolean tickerText = settings.enablePriceTickerText();

        List<Component> priceLines = resolvePriceLines(skyblockId, multiplier, tickerText);
        if (!priceLines.isEmpty()) {
            lines.addAll(priceLines);
            if (!store.isLastFetchFailed() && store.hasData() && store.getBazaarPrice(skyblockId).isPresent()) {
                appendHintLines(lines, logicalSize);
            }
        }
    }

    // ── Multiplier logic ───────────────────────────────────────────────────────

    /**
     * Computes the price multiplier:
     * <ul>
     *   <li>No modifier held → multiply by current stack count (shows 2x for a stack of 2)</li>
     *   <li>Shift (Current Amount) held → multiply by stack count; if stack is 1, use 64 instead</li>
     *   <li>Ctrl (Full Stack) held → always multiply by 64 (max stack size)</li>
     *   <li>Both held → multiply by both (stack multiplicatively)</li>
     * </ul>
     */
    private static int computeMultiplier(ItemStack stack, long logicalSize) {
        int currentAmount = (int) Math.min(logicalSize, Integer.MAX_VALUE);

        boolean ctrlHeld = PriceTooltipKeybinds.isFullStackHeld();
        boolean shiftHeld = PriceTooltipKeybinds.isCurrentAmountHeld();

        if (ctrlHeld && shiftHeld) {
            return (currentAmount <= 1) ? FULL_STACK_SIZE * FULL_STACK_SIZE : currentAmount * FULL_STACK_SIZE;
        } else if (ctrlHeld) {
            return FULL_STACK_SIZE;
        } else if (shiftHeld) {
            // Shift: show stack amount, but if stack is 1, show full stack instead
            return (currentAmount == 1) ? FULL_STACK_SIZE : currentAmount;
        }

        return 1;
    }

    // ── Hint lines (discoverability) ────────────────────────────────────────────

    /**
     * Appends subtle gray hint lines when a modifier is available but not held.
     * Uses the player's actual bound key names so hints remain accurate after rebinding.
     * Each hint is on its own line.
     */
    private static void appendHintLines(List<Component> lines, long stack) {
        boolean ctrlHeld = PriceTooltipKeybinds.isFullStackHeld();
        boolean shiftHeld = PriceTooltipKeybinds.isCurrentAmountHeld();

        String ctrlKey = PriceTooltipKeybinds.getFullStackKeyName();
        String shiftKey = PriceTooltipKeybinds.getCurrentAmountKeyName();

        boolean showCtrlHint = !ctrlHeld && !ctrlKey.isEmpty();
        boolean showShiftHint = !shiftHeld && !shiftKey.isEmpty();

        String shiftHintText = (stack == 1) ? " for x" + FULL_STACK_SIZE : " for x" + stack;

        if (showCtrlHint && showShiftHint) {
            lines.add(Component.literal("Hold ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(ctrlKey).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" for x" + FULL_STACK_SIZE + ", ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(shiftKey).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(shiftHintText).withStyle(ChatFormatting.DARK_GRAY)));
        } else if (showCtrlHint) {
            lines.add(Component.literal("Hold ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(ctrlKey).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" for x" + FULL_STACK_SIZE).withStyle(ChatFormatting.DARK_GRAY)));
        } else if (showShiftHint) {
            lines.add(Component.literal("Hold ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(shiftKey).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(shiftHintText).withStyle(ChatFormatting.DARK_GRAY)));
        }
    }

    // ── Cache resolution ──────────────────────────────────────────────────────

    /**
     * Returns cached price lines when available, otherwise builds and caches them.
     * A composite cache key is used so that different multipliers or ticker-text
     * states do not pollute the default cache entry.
     */
    private List<Component> resolvePriceLines(String skyblockId, int multiplier, boolean tickerText) {
        String cacheKey = buildCacheKey(skyblockId, multiplier, tickerText);

        Optional<PriceStore.PriceCacheEntry> cached = store.getTooltipCache(cacheKey);
        if (cached.isPresent()) {
            return cached.get().lines();
        }

        List<Component> lines = buildPriceLines(skyblockId, multiplier, tickerText);
        if (!lines.isEmpty()) {
            store.putTooltipCache(cacheKey, new PriceStore.PriceCacheEntry(lines, System.currentTimeMillis()));
        }
        return lines;
    }

    private String buildCacheKey(String skyblockId, int multiplier, boolean tickerText) {
        return skyblockId + "|m=" + multiplier + "|t=" + tickerText + "|round=" + settings.roundPriceNumbers() + "|bzBS=" + settings.showBazaarBuySell() + "|bzS=" + settings.showBazaarSpread() + "|failed=" + store.isLastFetchFailed();
    }

    // ── Line building ───────────────────────────────────────────────────────────

    private List<Component> buildPriceLines(String skyblockId, int multiplier, boolean tickerText) {
        if (!store.hasData()) {
            return buildErrorLines();
        }

        Optional<Double> lowestBin = store.getLowestBin(skyblockId);
        Optional<BazaarPrice> bazaar = store.getBazaarPrice(skyblockId);
        boolean roundNumbers = settings.roundPriceNumbers();

        if (lowestBin.isEmpty() && bazaar.isEmpty()) return List.of();

        List<Component> builder = new ArrayList<>(5);
        builder.add(Component.empty());

        // Stale-data banner when the last fetch failed
        if (store.isLastFetchFailed()) {
            builder.add(Component.literal("⚠ Prices may be outdated").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }

        lowestBin.ifPresent(price -> builder.add(priceLine("AH Lowest BIN", price, 1, tickerText, roundNumbers)));

        if (bazaar.isPresent()) {
            BazaarPrice bz = bazaar.get();
            if (settings.showBazaarBuySell()) {
                if (bz.buyPrice() > 0) {
                    builder.add(priceLine("BZ Buy Price", bz.buyPrice(), multiplier, tickerText, roundNumbers));
                }
                if (bz.sellPrice() > 0) {
                    builder.add(priceLine("BZ Sell Price", bz.sellPrice(), multiplier, tickerText, roundNumbers));
                }
            }
            if (settings.showBazaarSpread()) {
                if (bz.spread() > 0) {
                    builder.add(priceLine("BZ Spread", bz.spread(), multiplier, tickerText, roundNumbers));
                }
            }
        }

        return List.copyOf(builder);
    }

    /**
     * Builds a single compact error line shown when the API is unreachable.
     */
    private List<Component> buildErrorLines() {
        return List.of(
                Component.empty(),
                Component.literal("Prices: ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("API unreachable — last fetch failed (SBE)").withStyle(ChatFormatting.RED)));
    }

    /**
     * Formats a single price line: {@code §6<label>: §e<formatted coins> coins}.
     * When {@code tickerText} is {@code true} the coin value is rendered bold.
     * When {@code multiplier} is greater than 1, appends {@code (xN)} in dark gray
     * to indicate the price represents that many items.
     */
    private static MutableComponent priceLine(String label, double price, int multiplier, boolean tickerText, boolean roundNumbers) {
        double displayPrice = price * multiplier;
        String formatted = formatCoins(displayPrice, roundNumbers);

        MutableComponent coins = Component.literal(formatted + " coins");
        if (tickerText) {
            coins.withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
        } else {
            coins.withStyle(ChatFormatting.YELLOW);
        }

        MutableComponent line = Component.literal(label + ": ")
                .withStyle(ChatFormatting.GOLD)
                .append(coins);

        if (multiplier > 1) {
            line.append(Component.literal(" (x" + multiplier + ")").withStyle(ChatFormatting.DARK_GRAY));
        }

        return line;
    }

    /**
     * Formats a coin value for display. Uses suffixed notation for large values
     * (e.g. 1.2M, 3.5B) and comma-separated notation for smaller ones.
     */
    static String formatCoins(double value) {
        return formatCoins(value, true);
    }

    static String formatCoins(double value, boolean roundNumbers) {
        if (!roundNumbers) {
            return formatRawCoins(value);
        }
        if (value >= 1_000_000_000) {
            return String.format(Locale.US, "%.1fB", value / 1_000_000_000);
        }
        if (value >= 1_000_000) {
            return String.format(Locale.US, "%.1fM", value / 1_000_000);
        }
        return COIN_FORMAT.format(value);
    }

    private static String formatRawCoins(double value) {
        String plain = BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();

        int decimalIndex = plain.indexOf('.');
        String integerPart = decimalIndex >= 0 ? plain.substring(0, decimalIndex) : plain;
        String fractionPart = decimalIndex >= 0 ? plain.substring(decimalIndex) : "";

        String sign = "";
        if (integerPart.startsWith("-") || integerPart.startsWith("+")) {
            sign = integerPart.substring(0, 1);
            integerPart = integerPart.substring(1);
        }

        StringBuilder grouped = new StringBuilder(integerPart.length() + integerPart.length() / 3);
        for (int i = 0; i < integerPart.length(); i++) {
            if (i > 0 && (integerPart.length() - i) % 3 == 0) {
                grouped.append(',');
            }
            grouped.append(integerPart.charAt(i));
        }

        return sign + grouped + fractionPart;
    }
}
