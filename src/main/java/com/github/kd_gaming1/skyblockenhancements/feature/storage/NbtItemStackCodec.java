package com.github.kd_gaming1.skyblockenhancements.feature.storage;

import com.mojang.serialization.DataResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

/**
 * Bidirectional codec between {@link ItemStack} and Base64-encoded compressed NBT.
 *
 * <p>Uses {@link ItemStack#CODEC} with a {@link RegistryOps} context so that
 * DataComponents are preserved correctly on 1.21.11.
 */
public final class NbtItemStackCodec {

    private NbtItemStackCodec() {}

    /**
     * Encodes an item stack to a Base64 string.
     *
     * @param stack  the stack to encode; empty stacks yield {@code null}
     * @param lookup registry lookup for component serialization
     * @return Base64-encoded compressed NBT, or {@code null} if empty or encoding fails
     */
    public static String encode(ItemStack stack, HolderLookup.Provider lookup) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        RegistryOps<Tag> ops = lookup.createSerializationContext(NbtOps.INSTANCE);
        DataResult<Tag> result = ItemStack.CODEC.encodeStart(ops, stack);
        Optional<Tag> tagOpt = result.result();
        if (tagOpt.isEmpty() || !(tagOpt.get() instanceof CompoundTag compound)) {
            return null;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(compound, baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Decodes a Base64 string to an {@link ItemStack}.
     *
     * @param base64 the Base64-encoded compressed NBT
     * @param lookup registry lookup for component deserialization
     * @return the decoded stack, or {@link ItemStack#EMPTY} on failure
     */
    public static ItemStack decode(String base64, HolderLookup.Provider lookup) {
        if (base64 == null || base64.isEmpty()) {
            return ItemStack.EMPTY;
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            return ItemStack.EMPTY;
        }

        CompoundTag compound;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            compound = NbtIo.readCompressed(bais, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            return ItemStack.EMPTY;
        }

        RegistryOps<Tag> ops = lookup.createSerializationContext(NbtOps.INSTANCE);
        DataResult<ItemStack> result = ItemStack.CODEC.parse(ops, compound);
        return result.result().orElse(ItemStack.EMPTY);
    }

    /**
     * Decodes only the NBT tag from Base64, without resolving to an ItemStack.
     * Useful when the registry lookup is not yet available.
     *
     * @param base64 the Base64-encoded compressed NBT
     * @return the raw compound tag, or empty on failure
     */
    public static Optional<CompoundTag> decodeToTag(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
                return Optional.of(NbtIo.readCompressed(bais, NbtAccounter.unlimitedHeap()));
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
