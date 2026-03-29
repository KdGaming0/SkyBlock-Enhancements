package com.github.kd_gaming1.skyblockenhancements.mixin;

import java.util.List;
import java.util.Set;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin plugin that conditionally loads Modern UI compat mixins only when Modern UI is present.
 */
public class SkyblockEnhancementsMixinPlugin implements IMixinConfigPlugin {

    private static final String MODERNUI_TOOLTIP_MIXIN =
            "com.github.kd_gaming1.skyblockenhancements.mixin.tooltipscroll.TooltipScrollModernUIMixin";

    private boolean modernUIPresent;

    @Override
    public void onLoad(String mixinPackage) {
        modernUIPresent = FabricLoader.getInstance().isModLoaded("modernui");
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (MODERNUI_TOOLTIP_MIXIN.equals(mixinClassName)) {
            return modernUIPresent;
        }
        return true;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(
            String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(
            String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}