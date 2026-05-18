package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.gui;

import cc.cassian.rrv.common.gui.RrvClientSettingsScreen;
import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.mixin.access.ScreenAccessor;
import eu.midnightdust.lib.config.MidnightConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RrvClientSettingsScreen.class, remap = false)
public abstract class RrvClientSettingsScreenMixin extends Screen {

    @Shadow
    private int yPos;

    protected RrvClientSettingsScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"), remap = true)
    private void sbe$addSkyblockEnhancementsSection(CallbackInfo ci) {
        int buttonWidth = this.width / 3;
        int col1 = this.width / 4 - buttonWidth / 4;

        StringWidget hint = new StringWidget(
                Component.translatable("skyblock_enhancements.midnightconfig.rrvConfigHint"),
                this.font);
        hint.setX(this.width / 2 - hint.getWidth() / 2);
        hint.setY(yPos + 26);
        ((ScreenAccessor) this).sbe$addRenderableWidget(hint);
        yPos += 40;

        Button btn = Button.builder(
                        Component.translatable("skyblock_enhancements.midnightconfig.openFromRrv"),
                        button -> {
                            Minecraft client = Minecraft.getInstance();
                            client.schedule(() -> {
                                try {
                                    client.setScreen(MidnightConfig.getScreen(client.screen, SkyblockEnhancements.MOD_ID));
                                } catch (Exception e) {
                                    SkyblockEnhancements.LOGGER.error("Failed to open config menu", e);
                                }
                            });
                        })
                .pos(col1, yPos)
                .size(buttonWidth, 20)
                .build();
        ((ScreenAccessor) this).sbe$addRenderableWidget(btn);
        yPos += 22;
    }
}