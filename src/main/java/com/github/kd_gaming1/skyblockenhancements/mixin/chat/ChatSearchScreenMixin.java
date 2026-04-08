package com.github.kd_gaming1.skyblockenhancements.mixin.chat;

import com.github.kd_gaming1.skyblockenhancements.access.SBEChatAccess;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.search.ChatSearchLayout;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.search.ChatSearchState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds a search bar to the chat screen, toggled with Ctrl+F (or always shown if configured).
 *
 * <p>The search bar sits directly above the vanilla chat input and uses the same borderless
 * EditBox style with a semi-transparent background drawn manually. The vanilla input remains
 * fully functional at all times — clicking it simply moves focus away from the search bar.
 */
@Mixin(ChatScreen.class)
public abstract class ChatSearchScreenMixin extends Screen {

    @Shadow protected EditBox input;

    @Unique private EditBox sbe$searchBox;
    @Unique private String sbe$pendingQuery;

    // Semi-transparent dark background, same style as the vanilla chat input fill.
    @Unique private static final int SBE_SEARCH_BG = 0x60000000;

    // Hint overlay — shown for SBE_HINT_DURATION_MS after the screen opens,
    // only when the search bar is not already active.
    @Unique private static final long SBE_HINT_DURATION_MS = 4000L;
    @Unique private static final long SBE_HINT_FADE_MS     =  600L;
    @Unique private long sbe$hintShownAt = -1L;

    protected ChatSearchScreenMixin(Component title) {
        super(title);
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Inject(method = "init", at = @At("TAIL"))
    private void sbe$initSearchBar(CallbackInfo ci) {
        if (!SkyblockEnhancementsConfig.enableChatSearch) return;

        // Show the hint whenever the screen (re)opens and search is not already active.
        if (!ChatSearchState.isActive()) {
            sbe$hintShownAt = Util.getMillis();
        }

        // Auto-open when "always show" is configured
        if (SkyblockEnhancementsConfig.alwaysShowChatSearch && !ChatSearchState.isActive()) {
            ChatSearchState.setActive(true);
        }

        if (!ChatSearchState.isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        int y = ChatSearchLayout.searchBarY(height);

        sbe$searchBox = new EditBox(
                mc.font,
                4, y, width - 8, ChatSearchLayout.SEARCH_BAR_HEIGHT,
                Component.literal("Search chat..."));
        sbe$searchBox.setHint(Component.literal("Search chat... (Ctrl+F)"));
        sbe$searchBox.setMaxLength(128);
        sbe$searchBox.setResponder(this::sbe$onQueryChanged);
        sbe$searchBox.setBordered(false);
        sbe$searchBox.setCanLoseFocus(true);

        // Restore query across widget rebuilds (resize, Ctrl+F toggle, etc.)
        if (sbe$pendingQuery != null) {
            sbe$searchBox.setValue(sbe$pendingQuery);
            sbe$pendingQuery = null;
        } else {
            sbe$searchBox.setValue(ChatSearchState.getQuery());
        }

        addRenderableWidget(sbe$searchBox);
        if (ChatSearchState.isActive()) {
            input.setCanLoseFocus(true);
        }

        // Focus is set explicitly in sbe$openSearch() after rebuildWidgets() returns,
        // so no deferred schedule is needed here for the Ctrl+F toggle path.
        // The alwaysShowChatSearch path gets focus naturally since init runs at screen open.
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void sbe$clearSearchOnClose(CallbackInfo ci) {
        if (ChatSearchState.isActive()) {
            ChatSearchState.setActive(false);
            sbe$refreshChat();
        }
    }

    // ── Key handling ─────────────────────────────────────────────────────────

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void sbe$handleSearchKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!SkyblockEnhancementsConfig.enableChatSearch) return;

        int key = event.key();

        // Ctrl+F: toggle (or focus if always-show)
        if (key == GLFW.GLFW_KEY_F && event.hasControlDown()) {
            sbe$toggleSearch();
            cir.setReturnValue(true);
            return;
        }

        // Escape: clear query first, then close (or just clear if always-show)
        if (key == GLFW.GLFW_KEY_ESCAPE && ChatSearchState.isActive()) {
            if (sbe$searchBox != null && !sbe$searchBox.getValue().isEmpty()) {
                // First press: clear the query but keep the bar open
                sbe$searchBox.setValue("");
                ChatSearchState.setQuery("");
                sbe$refreshChat();
            } else if (SkyblockEnhancementsConfig.alwaysShowChatSearch) {
                return;
            } else {
                sbe$closeSearch();
            }
            cir.setReturnValue(true);
        }
    }

    // ── Render ───────────────────────────────────────────────────────────────

    /**
     * Draw the semi-transparent background fill for the search bar before widgets render.
     * This replaces the heavy background that EditBoxWidget would draw itself.
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void sbe$renderSearchBackground(GuiGraphics graphics, int mouseX, int mouseY,
                                            float delta, CallbackInfo ci) {
        if (!SkyblockEnhancementsConfig.enableChatSearch) return;
        if (!ChatSearchState.isActive() || sbe$searchBox == null) return;

        int y = ChatSearchLayout.searchBarY(height);
        int x1 = 2, x2 = width - 2;
        int y1 = y - 2, y2 = y + ChatSearchLayout.SEARCH_BAR_HEIGHT + 2;

        graphics.fill(x1, y1, x2, y2, SBE_SEARCH_BG);
        graphics.fill(x1,     y1,     x2, y1 + 1, 0xFFAAAAAA);
        graphics.fill(x1,     y2 - 1, x2, y2,     0xFFAAAAAA);
        graphics.fill(x1,     y1,     x1 + 1, y2, 0xFFAAAAAA);
        graphics.fill(x2 - 1, y1,     x2, y2,     0xFFAAAAAA);
    }

    /**
     * Draw a subtle "Press Ctrl+F to search" hint when the search bar is not open.
     * Fades out over SBE_HINT_FADE_MS at the end of SBE_HINT_DURATION_MS.
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void sbe$renderSearchHint(GuiGraphics graphics, int mouseX, int mouseY, float delta,
                                      CallbackInfo ci) {
        if (!SkyblockEnhancementsConfig.enableChatSearch) return;
        if (ChatSearchState.isActive() || sbe$hintShownAt < 0) return;

        long elapsed = Util.getMillis() - sbe$hintShownAt;
        if (elapsed >= SBE_HINT_DURATION_MS) return;

        // Compute alpha: fully opaque for most of the duration, then fade to 0.
        float alpha;
        long fadeStart = SBE_HINT_DURATION_MS - SBE_HINT_FADE_MS;
        if (elapsed < fadeStart) {
            alpha = 1.0f;
        } else {
            alpha = 1.0f - (float)(elapsed - fadeStart) / SBE_HINT_FADE_MS;
        }
        int a = (int)(alpha * 0xFF) & 0xFF;

        // Position: bottom-right corner just above the vanilla chat input.
        int inputY = height - 14;
        String hintText = "Ctrl+F to search";
        int textWidth = font.width(hintText);
        int x = width - textWidth - 6;
        int y = inputY - font.lineHeight - 3;

        graphics.fill(x - 3, y - 2, x + textWidth + 3, y + font.lineHeight + 2,
                (a / 2) << 24);
        int color = (a << 24) | 0x00AAAAAA;
        graphics.drawString(font, hintText, x, y, color, false);
    }

    /**
     * Draw the match-count overlay after widgets (so it appears on top of the text).
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void sbe$renderMatchCount(GuiGraphics graphics, int mouseX, int mouseY, float delta,
                                      CallbackInfo ci) {
        if (!SkyblockEnhancementsConfig.enableChatSearch) return;
        if (!ChatSearchState.isActive() || sbe$searchBox == null) return;

        if (ChatSearchState.isFiltering()) {
            SBEChatAccess access = (SBEChatAccess) Minecraft.getInstance().gui.getChat();
            int total = access.sbe$getAllMessages().size();
            int matching = ChatSearchState.countMatching(access.sbe$getAllMessages());
            String info = matching + "/" + total;
            int infoWidth = font.width(info);
            int infoX = sbe$searchBox.getX() + sbe$searchBox.getWidth() - infoWidth - 4;
            int infoY = ChatSearchLayout.searchBarY(height)
                    + (ChatSearchLayout.SEARCH_BAR_HEIGHT - 8) / 2;
            graphics.drawString(font, info, infoX, infoY, 0xFFAAAAAA, false);
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    @Unique
    private void sbe$toggleSearch() {
        if (!ChatSearchState.isActive()) {
            sbe$openSearch();
            return;
        }
        if (!SkyblockEnhancementsConfig.alwaysShowChatSearch) {
            sbe$closeSearch();
            return;
        }
        // Always-show mode: ping-pong focus between search box and chat input.
        if (sbe$searchBox != null) {
            boolean searchHasFocus = sbe$searchBox.isFocused();
            if (searchHasFocus) {
                setFocused(input);
                input.setFocused(true);
                sbe$searchBox.setFocused(false);
            } else {
                setFocused(sbe$searchBox);
                sbe$searchBox.setFocused(true);
                input.setFocused(false);
            }
        }
    }

    @Unique
    private void sbe$openSearch() {
        ChatSearchState.setActive(true);
        sbe$pendingQuery = "";
        rebuildWidgets();
        if (sbe$searchBox != null) {
            setFocused(sbe$searchBox);
            sbe$searchBox.setFocused(true);
            input.setFocused(false);
        }
    }

    @Unique
    private void sbe$closeSearch() {
        ChatSearchState.setActive(false);
        ChatSearchState.setQuery("");
        sbe$pendingQuery = null;
        sbe$refreshChat();
        rebuildWidgets();
        input.setCanLoseFocus(false);
        setFocused(input);
        input.setFocused(true);
    }

    @Unique
    private void sbe$onQueryChanged(String query) {
        ChatSearchState.setQuery(query);
        sbe$refreshChat();
    }

    @Unique
    private void sbe$refreshChat() {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.getChat().resetChatScroll();
        ((SBEChatAccess) mc.gui.getChat()).sbe$refreshMessages();
    }
}