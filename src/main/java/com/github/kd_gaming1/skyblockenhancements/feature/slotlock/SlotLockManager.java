package com.github.kd_gaming1.skyblockenhancements.feature.slotlock;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.KeybindCategories;
import com.github.kd_gaming1.skyblockenhancements.mixin.access.AbstractContainerScreenAccessor;
import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import com.github.kd_gaming1.skyblockenhancements.util.ProfileIdTracker;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Client-side, anti-cheat-safe slot locking.
 *
 * <p>Pressing the lock key (default {@code L}, rebindable in vanilla Controls) while hovering a
 * player-inventory slot toggles a lock on that <em>position</em>. The actual block lives in
 * {@code SlotClickLockMixin}, which cancels {@code slotClicked} at HEAD — before any container
 * packet is sent — so the client simply never performs the move.
 *
 * <p>Locks are keyed by the screen-stable {@link Slot#getContainerSlot()} index (0–40), scoped to
 * a bucket of {@code accountUuid} (off SkyBlock) or {@code accountUuid|<profileUuid>} (a specific
 * SkyBlock profile). The profile UUID is obtained via {@link ProfileIdTracker}, which parses
 * Hypixel's automatic {@code Profile ID: ...} chat messages; this is faster and more reliable than
 * parsing the tab list.
 * While on SkyBlock and the UUID is not yet known (and not cached), no locks are shown and no
 * new locks can be set, preventing a flash of the wrong bucket.
 */
public final class SlotLockManager {

    private static final String PROFILE_SEPARATOR = "|";

    private static SlotLockStorage storage;
    private static KeyMapping lockKey;

    private static String accountUuid;   // cached account UUID string (stable per session)
    private static String bucketKey;     // cached current bucket key

    private static boolean lockKeyWasDown = false;

    private SlotLockManager() {}

    /** Registers the keybind, tick handler, and disconnect reset; loads persisted locks. */
    public static void init(Path storagePath) {
        storage = new SlotLockStorage(storagePath);
        storage.load();

        lockKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping(
                        "key.skyblock_enhancements.lock_slot",
                        GLFW.GLFW_KEY_L,
                        KeybindCategories.GENERAL));

        ClientTickEvents.END_CLIENT_TICK.register(SlotLockManager::onClientTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            accountUuid = null;
            bucketKey = null;
        });
    }

    // ── Queries (used by the click-block and overlay mixins) ────────────────────

    /** {@code true} if the given player-inventory index is locked in the active bucket. */
    public static boolean isLocked(int containerSlot) {
        if (storage == null) return false;
        if (bucketKey == null) return false; // SkyBlock UUID not yet known — hide locks to avoid flashing the wrong bucket
        return storage.data().buckets.getOrDefault(bucketKey, Collections.emptySet()).contains(containerSlot);
    }

    // ── Toggle ──────────────────────────────────────────────────────────────────

    private static void toggle(int containerSlot) {
        if (storage == null) return;
        if (bucketKey == null) {
            // SkyBlock profile UUID not yet known; refuse rather than write to the wrong bucket.
            Minecraft client = Minecraft.getInstance();
            client.gui.setOverlayMessage(
                    Component.translatable("skyblock_enhancements.slotlock.waiting_profile"), false);
            return;
        }
        Set<Integer> set = storage.data().buckets.computeIfAbsent(bucketKey, k -> new HashSet<>());

        boolean nowLocked;
        if (set.contains(containerSlot)) {
            set.remove(containerSlot);
            nowLocked = false;
        } else {
            set.add(containerSlot);
            nowLocked = true;
        }
        if (set.isEmpty()) {
            storage.data().buckets.remove(bucketKey); // keep the file free of empty buckets
        }
        storage.save();
        playFeedback(nowLocked);
    }

    private static void playFeedback(boolean nowLocked) {
        Minecraft client = Minecraft.getInstance();
        client.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, nowLocked ? 1.4f : 0.8f));
        client.gui.setOverlayMessage(
                Component.translatable(nowLocked
                        ? "skyblock_enhancements.slotlock.locked"
                        : "skyblock_enhancements.slotlock.unlocked"), false);
    }

    // ── Tick: keypress edge detection ───────────────────────────────────────────

    private static void onClientTick(Minecraft client) {
        if (!SkyblockEnhancementsConfig.enableSlotLocking) {
            lockKeyWasDown = false;
            return;
        }

        // Refresh the active bucket once per tick. The render and click-block paths then read the
        // cached key directly instead of recomputing (and re-allocating the key string) for every
        // slot, every frame. A profile UUID arriving mid-session is still picked up within a tick.
        recomputeBucketKey();

        if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
            lockKeyWasDown = false;
            return;
        }

        // Edge-detected physical key state — fires once per press, immune to GLFW key repeat,
        // and works while a GUI is open (where KeyMapping.consumeClick does not).
        boolean down = isLockKeyDown(client);
        if (down && !lockKeyWasDown) {
            Slot slot = ((AbstractContainerScreenAccessor) screen).skyblockenhancements$getHoveredSlot();
            if (slot != null && slot.container instanceof Inventory) {
                toggle(slot.getContainerSlot());
            }
        }
        lockKeyWasDown = down;
    }

    private static boolean isLockKeyDown(Minecraft client) {
        if (lockKey == null) return false;
        InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(lockKey);
        if (key == null || key.getType() != InputConstants.Type.KEYSYM) return false;
        int keyCode = key.getValue();
        if (keyCode == InputConstants.UNKNOWN.getValue()) return false;
        return InputConstants.isKeyDown(client.getWindow(), keyCode);
    }

    // ── Bucket key ────────────────────────────────────────────────────────────

    private static void recomputeBucketKey() {
        if (HypixelLocationState.isOnSkyblock()) {
            var profileId = ProfileIdTracker.getProfileId();
            if (profileId.isPresent()) {
                bucketKey = accountUuid() + PROFILE_SEPARATOR + profileId.get();
                return;
            }
            // On SkyBlock but UUID unknown: use no bucket so locks stay hidden until we know the profile.
            bucketKey = null;
            return;
        }
        bucketKey = accountUuid();
    }

    private static String accountUuid() {
        if (accountUuid == null) {
            var user = Minecraft.getInstance().getUser();
            accountUuid = (user != null) ? user.getProfileId().toString() : "unknown";
        }
        return accountUuid;
    }
}
