package com.github.kd_gaming1.skyblockenhancements.feature.mining.track;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.mining.MiningAbilityTracker;
import com.github.kd_gaming1.skyblockenhancements.feature.mining.calc.BreakTimeCalculator;
import com.github.kd_gaming1.skyblockenhancements.feature.mining.data.BlockStrengthEntry;
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
 * State machine tracking one mining operation from target acquisition through
 * predicted break.
 *
 * <p>States: IDLE → PREVIEW → MINING → BROKEN → COOLDOWN → IDLE
 * <p>All state is primitive fields. Zero allocation per tick.
 */
public final class MiningProgressTracker {

    private MiningProgressTracker() {}

    public enum State {
        IDLE, PREVIEW, MINING, BROKEN, COOLDOWN
    }

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

    public static State getState() {
        return currentState;
    }

    public static int getElapsedTicks() {
        return elapsedTicks;
    }

    public static int getTotalBreakTicks() {
        return totalBreakTicks;
    }

    public static double getProgressPercent() {
        if (currentState == State.PREVIEW) return 0.0;
        if (currentState != State.MINING && currentState != State.BROKEN) return 0.0;
        if (totalBreakTicks <= 0) return 0.0;
        return BreakTimeCalculator.calculateProgressPercent(
                elapsedTicks, totalBreakTicks, cachedPingMs, cachedTps,
                SkyblockEnhancementsConfig.pingOffsetMarginMs);
    }

    public static boolean shouldSwitchNow() {
        return getProgressPercent() >= 1.0;
    }

    public static long getCurrentBlockPacked() {
        return currentBlockPacked;
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
        ticksSinceEnabled++;

        MiningAbilityTracker.tick();

        cachedPingMs = NetworkStats.hasEnoughData() ? NetworkStats.getPingMs() : 0;
        cachedTps = NetworkStats.getTps();

        switch (currentState) {
            case IDLE -> {
                if (isAttackKeyHeld(mc)) {
                    tryStartMining(mc);
                } else if (SkyblockEnhancementsConfig.pingOffsetShowOnLook) {
                    tryStartPreview(mc);
                }
            }
            case PREVIEW -> tickPreview(mc);
            case MINING -> tickMining(mc);
            case BROKEN -> tickBroken(mc);
            case COOLDOWN -> tickCooldown(mc);
        }

        if (ticksSinceEnabled > 100) {
            SkyblockStats.checkDemands();
        }
        syncRenderer();
    }

    private static void syncRenderer() {
        if (currentState == State.IDLE || currentState == State.COOLDOWN) {
            MiningOverlayRenderer.getInstance().clear();
            return;
        }
        if (currentBlockPacked == 0L) {
            MiningOverlayRenderer.getInstance().clear();
            return;
        }
        double progress = getProgressPercent();
        int tickForColor = currentState == State.PREVIEW ? 0 : elapsedTicks;
        BlockPos pos = BlockPos.of(currentBlockPacked);
        MiningOverlayRenderer.getInstance().updateProgress(progress, tickForColor, pos);
    }

    private static void tryStartPreview(Minecraft mc) {
        if (!MiningToolChecker.isHoldingMiningTool()) return;

        int miningSpeed = SkyblockStats.getMiningSpeedOrZero();
        if (miningSpeed <= 0) return;

        Optional<TargetBlockDetector.Result> target = TargetBlockDetector.getTargetBlock();
        if (target.isEmpty()) return;

        TargetBlockDetector.Result result = target.get();
        if (!MiningToolChecker.canMine(result.entry())) return;

        currentBlockPacked = result.pos().asLong();
        elapsedTicks = 0;
        totalBreakTicks = 0;
        currentState = State.PREVIEW;
    }

    private static void tryStartMining(Minecraft mc) {
        if (!MiningToolChecker.isHoldingMiningTool()) return;

        Optional<TargetBlockDetector.Result> target = TargetBlockDetector.getTargetBlock();
        if (target.isEmpty()) return;

        TargetBlockDetector.Result result = target.get();
        if (!MiningToolChecker.canMine(result.entry())) return;

        int miningSpeed = SkyblockStats.getMiningSpeedOrZero();
        if (miningSpeed <= 0) {
            if (ticksSinceEnabled > 100 && !SkyblockStats.hasMiningSpeed()) {
                SkyblockStats.demandStat("mining_speed", "Mining Speed");
            }
            return;
        }

        BlockStrengthEntry entry = result.entry();
        currentBlockPacked = result.pos().asLong();
        elapsedTicks = 0;
        totalBreakTicks = BreakTimeCalculator.calculateBreakTicks(entry, miningSpeed);
        lastToolSlot = mc.player != null ? mc.player.getInventory().getSelectedSlot() : -1;
        lastToolHash = HeldItemTracker.getToolInfo().hashCode();
        currentState = State.MINING;
    }

    private static void tickPreview(Minecraft mc) {
        if (isAttackKeyHeld(mc)) {
            transitionTo(State.IDLE);
            tryStartMining(mc);
            return;
        }
        if (toolChanged(mc)) {
            transitionTo(State.IDLE);
            return;
        }

        Optional<BlockPos> currentPos = TargetBlockDetector.getTargetPos();
        if (currentPos.isEmpty() || currentPos.get().asLong() != currentBlockPacked) {
            transitionTo(State.IDLE);
            return;
        }

        if (mc.level != null && mc.level.getBlockState(BlockPos.of(currentBlockPacked)).isAir()) {
            transitionTo(State.IDLE);
            return;
        }
    }

    private static void tickMining(Minecraft mc) {
        if (!isAttackKeyHeld(mc)) {
            transitionTo(State.IDLE);
            return;
        }
        if (toolChanged(mc)) {
            transitionTo(State.IDLE);
            return;
        }

        Optional<BlockPos> currentPos = TargetBlockDetector.getTargetPos();
        if (currentPos.isEmpty() || currentPos.get().asLong() != currentBlockPacked) {
            transitionTo(State.IDLE);
            return;
        }

        if (mc.level != null && mc.level.getBlockState(BlockPos.of(currentBlockPacked)).isAir()) {
            transitionTo(State.COOLDOWN);
            cooldownTicks = 2;
            return;
        }

        elapsedTicks++;

        double progress = BreakTimeCalculator.calculateProgressPercent(
                elapsedTicks, totalBreakTicks, cachedPingMs, cachedTps,
                SkyblockEnhancementsConfig.pingOffsetMarginMs);
        if (progress >= 1.0) {
            currentState = State.BROKEN;
        }
    }

    private static void tickBroken(Minecraft mc) {
        if (!isAttackKeyHeld(mc)) {
            transitionTo(State.IDLE);
            return;
        }
        if (toolChanged(mc)) {
            transitionTo(State.IDLE);
            return;
        }

        if (mc.level != null && mc.level.getBlockState(BlockPos.of(currentBlockPacked)).isAir()) {
            transitionTo(State.COOLDOWN);
            cooldownTicks = 2;
            return;
        }

        if (elapsedTicks > totalBreakTicks + 20) {
            transitionTo(State.IDLE);
        }
        elapsedTicks++;
    }

    private static void tickCooldown(Minecraft mc) {
        if (--cooldownTicks <= 0) {
            transitionTo(State.IDLE);
        }
    }

    private static void transitionTo(State newState) {
        currentState = newState;
        if (newState == State.IDLE) {
            currentBlockPacked = 0L;
            elapsedTicks = 0;
            totalBreakTicks = 0;
            MiningOverlayRenderer.getInstance().clear();
        }
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
