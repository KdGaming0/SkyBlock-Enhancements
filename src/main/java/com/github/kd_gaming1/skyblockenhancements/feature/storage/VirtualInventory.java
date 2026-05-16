package com.github.kd_gaming1.skyblockenhancements.feature.storage;

import com.mojang.serialization.DataResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

/**
 * Immutable snapshot of a storage page's contents.
 *
 * <p>Contains a fixed grid of {@link ItemStack}s (typically 45 = 5×9).
 * Serialization to compressed NBT is performed asynchronously via
 * {@link CompletableFuture} to avoid frame drops.
 */
public final class VirtualInventory {
    private final List<ItemStack> stacks;
    private final int rows;
    private final CompletableFuture<byte[]> serializationFuture;

    public VirtualInventory(List<ItemStack> stacks) {
        if (stacks.isEmpty() || stacks.size() > 54) {
            throw new IllegalArgumentException("Stack count must be 1..54");
        }
        if (stacks.size() % 9 != 0) {
            throw new IllegalArgumentException("Stack count must be a multiple of 9");
        }
        // Defensive copy — ItemStacks are mutable
        List<ItemStack> copy = new ArrayList<>(stacks.size());
        for (ItemStack s : stacks) {
            copy.add(s.copy());
        }
        this.stacks = List.copyOf(copy);
        this.rows = stacks.size() / 9;
        this.serializationFuture = CompletableFuture.supplyAsync(this::serializeToBytes);
    }

    public List<ItemStack> stacks() {
        return stacks;
    }

    public int rows() {
        return rows;
    }

    public int size() {
        return stacks.size();
    }

    public ItemStack get(int index) {
        return stacks.get(index);
    }

    public CompletableFuture<byte[]> getSerializationFuture() {
        return serializationFuture;
    }

    private byte[] serializeToBytes() {
        ListTag list = new ListTag();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                list.add(new CompoundTag());
            } else {
                try {
                    Tag tag = ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack).result().orElse(new CompoundTag());
                    list.add(tag);
                } catch (Exception e) {
                    list.add(new CompoundTag());
                }
            }
        }
        CompoundTag root = new CompoundTag();
        root.put("Inventory", list);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(root, out);
            return out.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    public static VirtualInventory deserialize(byte[] bytes, HolderLookup.Provider lookup) {
        if (bytes == null || bytes.length == 0) {
            return empty(5);
        }
        try {
            CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
            ListTag list = root.getListOrEmpty("Inventory");
            RegistryOps<Tag> ops = lookup.createSerializationContext(NbtOps.INSTANCE);
            List<ItemStack> stacks = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                Tag tag = list.get(i);
                if (tag instanceof CompoundTag compound && !compound.isEmpty()) {
                    DataResult<ItemStack> result = ItemStack.CODEC.parse(ops, compound);
                    stacks.add(result.result().orElse(ItemStack.EMPTY));
                } else {
                    stacks.add(ItemStack.EMPTY);
                }
            }
            // Pad or trim to valid size
            while (stacks.size() < 9) stacks.add(ItemStack.EMPTY);
            while (stacks.size() > 54) stacks.removeLast();
            int rows = stacks.size() / 9;
            int targetSize = rows * 9;
            while (stacks.size() < targetSize) stacks.add(ItemStack.EMPTY);
            return new VirtualInventory(stacks);
        } catch (Exception e) {
            return empty(5);
        }
    }

    public static VirtualInventory empty(int rows) {
        List<ItemStack> stacks = new ArrayList<>(rows * 9);
        for (int i = 0; i < rows * 9; i++) {
            stacks.add(ItemStack.EMPTY);
        }
        return new VirtualInventory(stacks);
    }
}
