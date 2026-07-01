package com.github.kd_gaming1.skyblockenhancements.feature.slotmanage;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.KeybindCategories;
import com.github.kd_gaming1.skyblockenhancements.feature.slotmanage.SlotStorage.Bucket;
import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import com.github.kd_gaming1.skyblockenhancements.util.ProfileIdTracker;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Client-side, anti-cheat-safe slot management: per-position <em>locking</em> and inventory→hotbar
 * <em>binding</em>, sharing one key (default {@code L}, "Slot Edit").
 *
 * <p><b>Lock:</b> a quick <em>tap</em> of the key toggles a lock on the hovered player-inventory slot
 * (works in any container). <b>Bind:</b> <em>hold</em> the key to enter bind mode (player inventory
 * only) — left-click an inventory slot then a hotbar slot to link; left-click a bound source or
 * right-click a bound slot to unlink. Outside bind mode, holding Shift previews the links;
 * shift+left-click a bound slot performs the swap and shift+right-click unlinks it.
 *
 * <p>State is keyed by {@link Slot#getContainerSlot()} (0–40, screen-stable) and bucketed per account
 * /SkyBlock profile via {@link ProfileIdTracker}. While on SkyBlock with an unknown profile, nothing
 * is shown and no changes can be made, avoiding a flash of the wrong bucket.
 */
public final class SlotManager {

    private static final String PROFILE_SEPARATOR = "|";
    /** Max key-hold duration still treated as a "tap" (lock toggle) rather than a hold (bind mode). */
    private static final long TAP_MAX_MILLIS = 250;

    private static SlotStorage storage;
    private static KeyMapping editKey;

    private static String accountUuid;
    private static String bucketKey;

    /** First inventory slot picked in a pending bind, as a {@code getContainerSlot()} index. */
    private static Integer pendingBindSlot;

    /** Slot the cursor pressed down on in bind mode, as a {@code getContainerSlot()} index (drag start). */
    private static Integer dragAnchorSlot;

    // Tap-vs-hold detection (driven by the tick handler).
    private static boolean editKeyWasDown;
    private static long editKeyDownAtMillis;
    private static boolean editModeClickConsumed;
    /** Slot under the cursor, stashed each frame by the overlay mixin so the tap handler can read it. */
    private static Slot contextHoveredSlot;

    private SlotManager() {}

    /** Registers the keybind, bucket-refresh tick handler, and disconnect reset; loads state. */
    public static void init(Path storagePath) {
        storage = new SlotStorage(storagePath);
        storage.load();

        editKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping(
                        "key.skyblock_enhancements.lock_slot",
                        GLFW.GLFW_KEY_L,
                        KeybindCategories.GENERAL));

        ClientTickEvents.END_CLIENT_TICK.register(SlotManager::onClientTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            accountUuid = null;
            bucketKey = null;
            pendingBindSlot = null;
            dragAnchorSlot = null;
            contextHoveredSlot = null;
        });
    }

    // ── Key/input state (read by the mixins and renderer) ───────────────────────

    /** {@code true} if the Slot Edit modifier key is physically held. */
    public static boolean isEditKeyDown() {
        return isKeyDown(boundEditKeyCode());
    }

    /** {@code true} if either Shift key is physically held. */
    public static boolean isShiftDown() {
        return isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || isKeyDown(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    /** Records that a click was handled while in bind mode, so the key release isn't a lock tap. */
    public static void notifyEditModeClick() {
        editModeClickConsumed = true;
    }

    /** Stashes the hovered slot (called every frame from the overlay mixin, for the tap handler). */
    public static void setHoveredSlot(Slot slot) {
        contextHoveredSlot = slot;
    }

    // ── Queries ─────────────────────────────────────────────────────────────────

    public static boolean isLocked(int containerSlot) {
        Bucket bucket = activeBucket(false);
        return bucket != null && bucket.locked.contains(containerSlot);
    }

    /** Hotbar button (0–8) bound to the given inventory position, or {@code null} if unbound. */
    public static Integer getBinding(int containerSlot) {
        Bucket bucket = activeBucket(false);
        return bucket == null ? null : bucket.binds.get(containerSlot);
    }

    /**
     * Inverse of {@link #getBinding}: the first inventory position bound to the given hotbar slot, or
     * {@code null} if none. Lets a shift-click on the hotbar <em>target</em> pull its bound item down.
     */
    public static Integer getSourceBoundToHotbar(int hotbarSlot) {
        Bucket bucket = activeBucket(false);
        if (bucket == null) return null;
        for (Map.Entry<Integer, Integer> entry : bucket.binds.entrySet()) {
            if (entry.getValue() == hotbarSlot) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Unmodifiable view of the active bucket's inventory→hotbar binds, for rendering. */
    public static Map<Integer, Integer> getCurrentBindings() {
        Bucket bucket = activeBucket(false);
        return bucket == null ? Collections.emptyMap() : Collections.unmodifiableMap(bucket.binds);
    }

    public static Integer getPendingBindSlot() {
        return pendingBindSlot;
    }

    // ── Mutations ───────────────────────────────────────────────────────────────

    public static void toggleLock(int containerSlot) {
        Bucket bucket = activeBucket(true);
        if (bucket == null) return;
        boolean nowLocked = bucket.locked.add(containerSlot);
        if (!nowLocked) {
            bucket.locked.remove(containerSlot);
        }
        pruneAndSave(bucket);
        playSound(nowLocked);
        overlay(nowLocked
                ? "skyblock_enhancements.slotlock.locked"
                : "skyblock_enhancements.slotlock.unlocked");
    }

    /**
     * Bind-mode left-click. A hotbar slot completes a pending bind; a bound inventory slot is unbound;
     * any other player-inventory slot becomes the new pending selection.
     */
    public static void handleBindClick(Slot slot) {
        if (!(slot.container instanceof Inventory)) return;
        Bucket bucket = activeBucket(true);
        if (bucket == null) return;

        int containerSlot = slot.getContainerSlot();
        boolean isHotbar = containerSlot >= 0 && containerSlot <= 8;

        if (!isHotbar && bucket.binds.containsKey(containerSlot)) {
            removeBind(bucket, containerSlot);
            return;
        }
        if (isHotbar) {
            if (pendingBindSlot != null) {
                bucket.binds.put(pendingBindSlot, containerSlot);
                pendingBindSlot = null;
                pruneAndSave(bucket);
                playSound(true);
                overlay("skyblock_enhancements.slotbind.created");
            }
            return;
        }
        pendingBindSlot = containerSlot; // select / replace the pending source
    }

    /** Records the slot a bind-mode left-press started on, so a release elsewhere can be a drag. */
    public static void beginDrag(int containerSlot) {
        dragAnchorSlot = containerSlot;
    }

    /**
     * Bind-mode left-release. Completes a bind when the press started on one side (inventory source
     * 9–40 or hotbar 0–8) and released on the opposite side — the "hold key + drag item to slot"
     * gesture. A same-slot release is a plain click, already handled on press, so it is ignored here.
     */
    public static void handleBindDragRelease(Slot rel) {
        Integer anchor = dragAnchorSlot;
        dragAnchorSlot = null;
        if (anchor == null || rel == null || !(rel.container instanceof Inventory)) return;

        int relSlot = rel.getContainerSlot();
        if (relSlot == anchor) return;

        boolean anchorHotbar = anchor >= 0 && anchor <= 8;
        boolean relHotbar = relSlot >= 0 && relSlot <= 8;
        if (anchorHotbar == relHotbar) return;

        int source = anchorHotbar ? relSlot : anchor;
        int hotbar = anchorHotbar ? anchor : relSlot;

        Bucket bucket = activeBucket(true);
        if (bucket == null) return;
        bucket.binds.put(source, hotbar);
        pendingBindSlot = null;
        pruneAndSave(bucket);
        playSound(true);
        overlay("skyblock_enhancements.slotbind.created");
    }

    /** Explicit unlink (right-click in bind mode, or shift+right-click). Returns whether it removed one. */
    public static boolean unbindSlot(Slot slot) {
        if (!(slot.container instanceof Inventory)) return false;
        Bucket bucket = activeBucket(false);
        if (bucket == null) return false;
        int containerSlot = slot.getContainerSlot();
        if (!bucket.binds.containsKey(containerSlot)) return false;
        removeBind(bucket, containerSlot);
        return true;
    }

    private static void removeBind(Bucket bucket, int containerSlot) {
        bucket.binds.remove(containerSlot);
        if (Objects.equals(pendingBindSlot, containerSlot)) {
            pendingBindSlot = null;
        }
        pruneAndSave(bucket);
        playSound(false);
        overlay("skyblock_enhancements.slotbind.removed");
    }

    // ── Tick: bucket refresh + tap-to-lock detection ────────────────────────────

    private static void onClientTick(Minecraft client) {
        if (!SkyblockEnhancementsConfig.enableSlotLocking
                && !SkyblockEnhancementsConfig.enableSlotBinding) {
            editKeyWasDown = false;
            return;
        }
        recomputeBucketKey();

        boolean down = isEditKeyDown();
        if (down && !editKeyWasDown) {
            editKeyDownAtMillis = Util.getMillis();
            editModeClickConsumed = false;
        } else if (!down && editKeyWasDown) {
            maybeTapLock(client);
        }
        editKeyWasDown = down;
    }

    /** On key release, a short hold with no click toggles the lock on the hovered slot. */
    private static void maybeTapLock(Minecraft client) {
        if (!SkyblockEnhancementsConfig.enableSlotLocking) return;
        if (editModeClickConsumed) return;
        if (Util.getMillis() - editKeyDownAtMillis > TAP_MAX_MILLIS) return;
        if (!(client.screen instanceof AbstractContainerScreen<?>)) return;
        Slot hovered = contextHoveredSlot;
        if (hovered != null && hovered.container instanceof Inventory) {
            toggleLock(hovered.getContainerSlot());
        }
    }

    // ── Internals ───────────────────────────────────────────────────────────────

    private static Bucket activeBucket(boolean createIfNeeded) {
        if (storage == null || bucketKey == null) {
            if (createIfNeeded && bucketKey == null) {
                Minecraft.getInstance().gui.setOverlayMessage(
                        Component.translatable("skyblock_enhancements.slotlock.waiting_profile"), false);
            }
            return null;
        }
        if (createIfNeeded) {
            return storage.data().buckets.computeIfAbsent(bucketKey, k -> new Bucket());
        }
        return storage.data().buckets.get(bucketKey);
    }

    private static void pruneAndSave(Bucket bucket) {
        if (bucket.locked.isEmpty() && bucket.binds.isEmpty()) {
            storage.data().buckets.remove(bucketKey);
        }
        storage.save();
    }

    private static int boundEditKeyCode() {
        if (editKey == null) return InputConstants.UNKNOWN.getValue();
        InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(editKey);
        if (key == null || key.getType() != InputConstants.Type.KEYSYM) {
            return InputConstants.UNKNOWN.getValue();
        }
        return key.getValue();
    }

    private static boolean isKeyDown(int keyCode) {
        if (keyCode == InputConstants.UNKNOWN.getValue()) return false;
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), keyCode);
    }

    private static void playSound(boolean positive) {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, positive ? 1.4f : 0.8f));
    }

    private static void overlay(String translationKey) {
        Minecraft.getInstance().gui.setOverlayMessage(Component.translatable(translationKey), false);
    }

    private static void recomputeBucketKey() {
        if (HypixelLocationState.isOnSkyblock()) {
            var profileId = ProfileIdTracker.getProfileId();
            if (profileId.isPresent()) {
                bucketKey = accountUuid() + PROFILE_SEPARATOR + profileId.get();
                return;
            }
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
