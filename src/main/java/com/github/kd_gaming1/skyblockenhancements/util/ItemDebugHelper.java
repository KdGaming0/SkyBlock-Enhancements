package com.github.kd_gaming1.skyblockenhancements.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Debug utility that dumps the complete NBT and component data of an ItemStack
 * so you can see exactly what SkyBlock puts on items (especially enchanted books).
 *
 * <p>Output is sent as chat lines so you can scroll through it in-game.
 * Call {@link #dumpToChat(ItemStack)} from a command handler.
 */
public final class ItemDebugHelper {

    private static final String HEADER = "§7[§bSBE Debug§7] §e§l--- Item Debug Dump ---";
    private static final String FOOTER = "§7[§bSBE Debug§7] §e§l--- End Dump ---";

    private ItemDebugHelper() {}

    /**
     * Dumps everything about the given stack to the player's chat.
     * If stack is empty, prints an error message.
     */
    public static void dumpToChat(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            chat("§cHold an item and try again.");
            return;
        }

        chat(HEADER);

        // 1. Basic item info
        chat("§6Item: §f" + stack.getItem().getDescriptionId());
        chat("§6Count: §f" + stack.getCount());
        chat("§6Max Stack: §f" + stack.getMaxStackSize());
        chat("§6Damage: §f" + stack.getDamageValue());

        // 2. DataComponents (Minecraft 1.20.5+)
        chat("");
        chat("§6§lDataComponents:");
        var components = stack.getComponents();
        if (components != null) {
            for (var component : components) {
                String key = component.type().toString();
                Object value = stack.get(component.type());
                chat("  §7- §e" + key + "§7 = §f" + String.valueOf(value));
            }
        } else {
            chat("  §7(none)");
        }

        // 3. CUSTOM_DATA (the NBT tag SkyBlock uses)
        chat("");
        chat("§6§lCUSTOM_DATA NBT:");
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            // Pretty-print the full NBT
            String prettyNbt = prettyPrintNbt(tag, 0);
            for (String line : prettyNbt.split("\n")) {
                chat("  " + line);
            }

            // 4. SkyBlock-specific extractions
            chat("");
            chat("§6§lSkyBlock Extraction:");

            // Raw ID
            String rawId = tag.getStringOr("id", "");
            chat("  §7extractSkyblockId() = §f" + (rawId.isEmpty() ? "§cnull" : "§a" + rawId));

            // Price lookup ID (the enchantment key logic)
            String priceId = SkyblockItemUtil.getPriceLookupId(stack);
            chat("  §7getPriceLookupId()  = §f" + (priceId == null ? "§cnull" : "§a" + priceId));

            // Enchantment details for books
            var enchantsOpt = tag.getCompound("enchantments");
            if (enchantsOpt.isPresent()) {
                CompoundTag enchants = enchantsOpt.get();
                chat("");
                chat("  §6§lEnchantments map:");
                Set<String> keys = enchants.keySet();
                for (String enchantName : keys) {
                    int level = enchants.getIntOr(enchantName, -1);
                    String apiKey = "ENCHANTMENT_" + enchantName.toUpperCase() + "_" + level;
                    chat("    §7- §e" + enchantName + " §7level=§f" + level);
                    chat("      §7API key: §a" + apiKey);
                }
            }

            // Other common SkyBlock NBT keys
            String[] commonKeys = {"uuid", "timestamp", "generator", "originTag"};
            boolean foundAny = false;
            for (String key : commonKeys) {
                if (tag.contains(key)) {
                    if (!foundAny) {
                        chat("");
                        chat("  §6§lOther SkyBlock tags:");
                        foundAny = true;
                    }
                    Tag valueTag = tag.get(key);
                    chat("    §7- §e" + key + "§7 = §f" + String.valueOf(valueTag));
                }
            }

        } else {
            chat("  §7(no CUSTOM_DATA component)");
            chat("");
            chat("  §c§lNote: This item has no SkyBlock NBT data.");
        }

        chat(FOOTER);
    }

    // ── Pretty-printing ──────────────────────────────────────────────────────────

    private static String prettyPrintNbt(CompoundTag tag, int indent) {
        if (tag.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder();
        String pad = "  ".repeat(indent);
        Set<String> keys = tag.keySet();
        for (String key : keys) {
            Tag value = tag.get(key);
            if (value instanceof CompoundTag compound) {
                sb.append(pad).append("§e").append(key).append("§7: {\n");
                sb.append(prettyPrintNbt(compound, indent + 1));
                sb.append(pad).append("§7}");
            } else if (value != null) {
                String valueStr = String.valueOf(value);
                // Truncate very long strings (e.g. base64 textures)
                if (valueStr.length() > 80) {
                    valueStr = valueStr.substring(0, 77) + "...§7 (" + (valueStr.length() - 77) + " more chars)";
                }
                sb.append(pad).append("§e").append(key).append("§7 = §f").append(valueStr);
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    /** Sends a message to the local player's chat. boolean=false means regular chat (not action bar). */
    private static void chat(String message) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(message));
        }
    }
}
