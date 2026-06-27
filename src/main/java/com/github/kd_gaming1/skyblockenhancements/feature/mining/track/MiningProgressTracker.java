/*
 * Mining-state handling adapted from Revvilon/PingOffsetMiner (worldTickEvent),
 * CC0-1.0: https://github.com/Revvilon/PingOffsetMiner
 * See THIRD_PARTY_LICENSES.md for the full attribution.
 */

package com.github.kd_gaming1.skyblockenhancements.feature.mining.track;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.mining.calc.BreakTimeCalculator;
import com.github.kd_gaming1.skyblockenhancements.feature.mining.detect.MiningToolChecker;
import com.github.kd_gaming1.skyblockenhancements.feature.mining.detect.TargetBlockDetector;
import com.github.kd_gaming1.skyblockenhancements.feature.mining.render.MiningOverlayRenderer;
import com.github.kd_gaming1.skyblockenhancements.util.NetworkStats;
import com.github.kd_gaming1.skyblockenhancements.util.tab.SkyblockStats;
import com.github.kd_gaming1.skyblockenhancements.util.tool.HeldItemTracker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.Optional;

/**
 * Client-tick state machine for one mining operation, from acquiring a target
 * through its predicted break.
 *
 * <p>States: {@code IDLE → PREVIEW → MINING → BROKEN → COOLDOWN → IDLE}. All
 * state is primitive fields; the only allocation per tick is the overlay
 * snapshot handed to {@link MiningOverlayRenderer}.
 */
public final class MiningProgressTracker {

    private MiningProgressTracker() {}

    private enum State {
        IDLE, PREVIEW, MINING, BROKEN, COOLDOWN
    }

    /** Ticks the overlay lingers after the block is gone, smoothing the break frame. */
    private static final int COOLDOWN_TICKS = 2;
    /** Extra ticks the BROKEN cue stays up if the server is slow to confirm the break. */
    private static final int BROKEN_LINGER_TICKS = 20;
    /** Grace period before nagging the player to enable the Mining Speed stat widget. */
    private static final int STAT_DEMAND_GRACE_TICKS = 100;

    private static State currentState = State.IDLE;
    private static long currentBlockPacked = 0L;
    private static int elapsedTicks = 0;
    private static int totalBreakTicks = 0;
    private static int lastToolSlot = -1;
    private static int lastToolHash = 0;
    private static int cooldownTicks = 0;
    private static boolean registered = false;

    private static int cachedPingMs = -1;
    private static double cachedTps = 20.0;

    private static int ticksSinceEnabled = 0;
    private static boolean featureWasEnabled = false;

    public static void register() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(MiningProgressTracker::onClientTick);
    }

    public static void reset() {
        currentState = State.IDLE;
        currentBlockPacked = 0L;
        elapsedTicks = 0;
        totalBreakTicks = 0;
        lastToolSlot = -1;
        lastToolHash = 0;
        cooldownTicks = 0;
        ticksSinceEnabled = 0;
        featureWasEnabled = false;
        SkyblockStats.clearDemands();
    }

    private static void onClientTick(Minecraft mc) {
        if (!SkyblockEnhancementsConfig.enablePingOffsetMining) {
            if (currentState != State.IDLE) {
                reset();
                MiningOverlayRenderer.getInstance().clear();
            }
            featureWasEnabled = false;
            return;
        }

        if (!featureWasEnabled) {
            ticksSinceEnabled = 0;
            featureWasEnabled = true;
        }
        if (ticksSinceEnabled <= STAT_DEMAND_GRACE_TICKS) {
            ticksSinceEnabled++;
        }

        cachedPingMs = NetworkStats.hasEnoughData() ? NetworkStats.getPingMs() : 0;
        cachedTps = NetworkStats.getTps();

        switch (currentState) {
            case IDLE -> {
                if (isAttackKeyHeld(mc)) {
                    tryStartMining(mc);
                } else if (SkyblockEnhancementsConfig.pingOffsetShowOnLook) {
                    tryStartPreview();
                }
            }
            case PREVIEW -> tickPreview(mc);
            case MINING -> tickMining(mc);
            case BROKEN -> tickBroken(mc);
            case COOLDOWN -> tickCooldown();
        }

        syncRenderer();
    }

    // ── Renderer sync ─────────────────────────────────────────────────────────

    private static void syncRenderer() {
        if (currentState == State.IDLE || currentState == State.COOLDOWN
                || currentBlockPacked == 0L) {
            MiningOverlayRenderer.getInstance().clear();
            return;
        }
        MiningOverlayRenderer.getInstance()
                .updateProgress(currentProgress(), BlockPos.of(currentBlockPacked));
    }

    private static double currentProgress() {
        if (currentState != State.MINING && currentState != State.BROKEN) return 0.0;
        return BreakTimeCalculator.calculateProgressPercent(
                elapsedTicks, totalBreakTicks, cachedPingMs, cachedTps,
                SkyblockEnhancementsConfig.pingOffsetMarginMs);
    }

    // ── Acquisition ─────────────────────────────────────────────────────────

    private static void tryStartPreview() {
        if (!MiningToolChecker.isHoldingMiningTool()) return;
        if (SkyblockStats.getMiningSpeedOrZero() <= 0) return;

        Optional<TargetBlockDetector.Result> target = TargetBlockDetector.getTargetBlock();
        if (target.isEmpty() || !MiningToolChecker.canMine(target.get().entry())) return;

        currentBlockPacked = target.get().pos().asLong();
        elapsedTicks = 0;
        totalBreakTicks = 0;
        currentState = State.PREVIEW;
    }

    /**
     * Tries to begin mining the targeted block. Returns {@code true} and moves to
     * {@link State#MINING} on success; on failure it leaves the machine's fields
     * untouched (so a running preview isn't cleared mid-tick) and returns {@code false}.
     */
    private static boolean tryStartMining(Minecraft mc) {
        if (!MiningToolChecker.isHoldingMiningTool()) return false;

        Optional<TargetBlockDetector.Result> target = TargetBlockDetector.getTargetBlock();
        if (target.isEmpty() || !MiningToolChecker.canMine(target.get().entry())) return false;

        int miningSpeed = SkyblockStats.getMiningSpeedOrZero();
        if (miningSpeed <= 0) {
            demandMiningSpeedStat();
            return false;
        }

        TargetBlockDetector.Result result = target.get();
        currentBlockPacked = result.pos().asLong();
        elapsedTicks = 0;
        totalBreakTicks = BreakTimeCalculator.calculateBreakTicks(result.entry(), miningSpeed);
        lastToolSlot = mc.player != null ? mc.player.getInventory().getSelectedSlot() : -1;
        lastToolHash = HeldItemTracker.getToolInfo().hashCode();
        currentState = State.MINING;
        return true;
    }

    private static void demandMiningSpeedStat() {
        if (ticksSinceEnabled > STAT_DEMAND_GRACE_TICKS && !SkyblockStats.hasMiningSpeed()) {
            SkyblockStats.demandStat("mining_speed", "Mining Speed",
                    "/tab -> Stats Widget -> Shown Stats -> Mining Speed",
                    "Needed for Ping Offset Mining feature.");
            SkyblockStats.checkDemands();
        }
    }

    // ── Per-state ticks ───────────────────────────────────────────────────────

    private static void tickPreview(Minecraft mc) {
        if (isAttackKeyHeld(mc)) {
            if (!tryStartMining(mc)) transitionToIdle();
            return;
        }
        if (toolChanged(mc) || !stillAimingAtTarget() || targetBlockIsAir(mc)) {
            transitionToIdle();
        }
    }

    private static void tickMining(Minecraft mc) {
        if (!isAttackKeyHeld(mc) || toolChanged(mc) || !stillAimingAtTarget()) {
            transitionToIdle();
            return;
        }
        if (targetBlockIsAir(mc)) {
            startCooldown();
            return;
        }

        elapsedTicks++;
        if (currentProgress() >= 1.0) {
            currentState = State.BROKEN;
        }
    }

    private static void tickBroken(Minecraft mc) {
        if (!isAttackKeyHeld(mc) || toolChanged(mc)) {
            transitionToIdle();
            return;
        }
        if (targetBlockIsAir(mc)) {
            startCooldown();
            return;
        }
        if (elapsedTicks > totalBreakTicks + BROKEN_LINGER_TICKS) {
            transitionToIdle();
        }
        elapsedTicks++;
    }

    private static void tickCooldown() {
        if (--cooldownTicks <= 0) {
            transitionToIdle();
        }
    }

    // ── Transitions & predicates ────────────────────────────────────────────

    private static void startCooldown() {
        currentState = State.COOLDOWN;
        cooldownTicks = COOLDOWN_TICKS;
    }

    private static void transitionToIdle() {
        currentState = State.IDLE;
        currentBlockPacked = 0L;
        elapsedTicks = 0;
        totalBreakTicks = 0;
        MiningOverlayRenderer.getInstance().clear();
    }

    private static boolean stillAimingAtTarget() {
        Optional<BlockPos> pos = TargetBlockDetector.getTargetPos();
        return pos.isPresent() && pos.get().asLong() == currentBlockPacked;
    }

    private static boolean targetBlockIsAir(Minecraft mc) {
        return mc.level != null && mc.level.getBlockState(BlockPos.of(currentBlockPacked)).isAir();
    }

    private static boolean isAttackKeyHeld(Minecraft mc) {
        return mc.options.keyAttack.isDown();
    }

    private static boolean toolChanged(Minecraft mc) {
        if (mc.player == null) return true;
        int currentSlot = mc.player.getInventory().getSelectedSlot();
        int currentHash = HeldItemTracker.getToolInfo().hashCode();
        boolean changed = currentSlot != lastToolSlot || currentHash != lastToolHash;
        lastToolSlot = currentSlot;
        lastToolHash = currentHash;
        return changed;
    }
}
