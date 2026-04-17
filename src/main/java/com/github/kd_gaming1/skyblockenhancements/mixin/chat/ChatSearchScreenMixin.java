package com.github.kd_gaming1.skyblockenhancements.mixin.chat;

import com.github.kd_gaming1.skyblockenhancements.access.SBEChatAccess;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.ChatFeatureState;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.search.ChatSearchController;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.search.ChatSearchTheme;
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
 * Adds a borderless search bar to {@link ChatScreen}, toggled with Ctrl+F or kept always-on
 * per config. Layout and colours live in {@link ChatSearchTheme}; query and filter state live
 * in {@link ChatSearchController}. This mixin is just the presentation layer.
 */
@Mixin(ChatScreen.class)
public abstract class ChatSearchScreenMixin extends Screen {

    @Unique
    private static final int SEARCH_INPUT_MAX_LEN = 128;

    @Shadow protected EditBox input;

    @Unique private EditBox sbe$searchBox;
    @Unique private String sbe$pendingQuery;
    @Unique private long sbe$hintShownAt = -1L;

    protected ChatSearchScreenMixin(Component title) { super(title); }

    // ── Lifecycle ────────────────────────────────────────────────────────

    @Inject(method = "init", at = @At("TAIL"))
    private void sbe$initSearchBar(CallbackInfo ci) {
        if (!SkyblockEnhancementsConfig.enableChatSearch) return;

        ChatSearchController search = ChatFeatureState.get().search();

        if (!search.isActive()) {
            sbe$hintShownAt = Util.getMillis();
        }

        if (SkyblockEnhancementsConfig.alwaysShowChatSearch && !search.isActive()) {
            search.setActive(true);
        }

        if (!search.isActive()) return;

        sbe$searchBox = sbe$buildSearchBox(search);
        addRenderableWidget(sbe$searchBox);
        input.setCanLoseFocus(true);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void sbe$clearSearchOnClose(CallbackInfo ci) {
        ChatSearchController search = ChatFeatureState.get().search();
        if (search.isActive()) {
            search.setActive(false);
            sbe$refreshChat();
        }
    }

    // ── Keys ─────────────────────────────────────────────────────────────

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void sbe$handleSearchKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!SkyblockEnhancementsConfig.enableChatSearch) return;

        int key = event.key();
        ChatSearchController search = ChatFeatureState.get().search();

        if (key == GLFW.GLFW_KEY_F && event.hasControlDown()) {
            sbe$toggleSearch();
            cir.setReturnValue(true);
            return;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE && search.isActive() && sbe$handleEscape(search)) {
            cir.setReturnValue(true);
        }
    }

    /** @return {@code true} if Esc was consumed; {@code false} to let the screen close. */
    @Unique
    private boolean sbe$handleEscape(ChatSearchController search) {
        // First press with text: clear the query but keep the bar open.
        if (sbe$searchBox != null && !sbe$searchBox.getValue().isEmpty()) {
            sbe$searchBox.setValue("");
            search.setQuery("");
            sbe$refreshChat();
            return true;
        }
        // Pinned bar with empty query: defer to the screen so Esc closes chat.
        if (SkyblockEnhancementsConfig.alwaysShowChatSearch) {
            return false;
        }
        sbe$closeSearch();
        return true;
    }

    // ── Rendering ────────────────────────────────────────────────────────

    @Inject(method = "render", at = @At("HEAD"))
    private void sbe$renderSearchBackground(
            GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!SkyblockEnhancementsConfig.enableChatSearch) return;
        if (!ChatFeatureState.get().search().isActive() || sbe$searchBox == null) return;

        int y = ChatSearchTheme.searchBarY(height);
        int x1 = 2, x2 = width - 2;
        int y1 = y - 2, y2 = y + ChatSearchTheme.SEARCH_BAR_HEIGHT + 2;

        graphics.fill(x1, y1, x2, y2, ChatSearchTheme.BACKGROUND);
        // 1px outline
        graphics.fill(x1, y1, x2, y1 + 1, ChatSearchTheme.BORDER);
        graphics.fill(x1, y2 - 1, x2, y2, ChatSearchTheme.BORDER);
        graphics.fill(x1, y1, x1 + 1, y2, ChatSearchTheme.BORDER);
        graphics.fill(x2 - 1, y1, x2, y2, ChatSearchTheme.BORDER);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void sbe$renderSearchHint(
            GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!SkyblockEnhancementsConfig.enableChatSearch) return;
        if (ChatFeatureState.get().search().isActive() || sbe$hintShownAt < 0) return;

        long elapsed = Util.getMillis() - sbe$hintShownAt;
        if (elapsed >= ChatSearchTheme.HINT_DURATION_MS) return;

        int alpha = sbe$fadeAlpha(elapsed);
        String hintText = "Ctrl+F to search";
        int textWidth = font.width(hintText);
        int inputY = height - 14;
        int x = width - textWidth - 6;
        int y = inputY - font.lineHeight - 3;

        graphics.fill(x - 3, y - 2, x + textWidth + 3, y + font.lineHeight + 2, (alpha / 2) << 24);
        int color = (alpha << 24) | ChatSearchTheme.HINT_RGB;
        graphics.drawString(font, hintText, x, y, color, false);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void sbe$renderMatchCount(
            GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!SkyblockEnhancementsConfig.enableChatSearch) return;

        ChatSearchController search = ChatFeatureState.get().search();
        if (!search.isActive() || sbe$searchBox == null || !search.isFiltering()) return;

        SBEChatAccess access = (SBEChatAccess) Minecraft.getInstance().gui.getChat();
        int total = access.sbe$getAllMessages().size();
        int matching = search.countMatching(access.sbe$getAllMessages());
        String info = matching + "/" + total;
        int infoWidth = font.width(info);
        int infoX = sbe$searchBox.getX() + sbe$searchBox.getWidth() - infoWidth - 4;
        int infoY = ChatSearchTheme.searchBarY(height)
                + (ChatSearchTheme.SEARCH_BAR_HEIGHT - 8) / 2;
        graphics.drawString(font, info, infoX, infoY, ChatSearchTheme.MATCH_COUNT_TEXT, false);
    }

    @Unique
    private static int sbe$fadeAlpha(long elapsed) {
        long fadeStart = ChatSearchTheme.HINT_DURATION_MS - ChatSearchTheme.HINT_FADE_MS;
        float alpha = elapsed < fadeStart
                ? 1f
                : 1f - (elapsed - fadeStart) / (float) ChatSearchTheme.HINT_FADE_MS;
        return (int) (alpha * 0xFF) & 0xFF;
    }

    // ── Internal state changes ───────────────────────────────────────────

    @Unique
    private EditBox sbe$buildSearchBox(ChatSearchController search) {
        Minecraft mc = Minecraft.getInstance();
        int y = ChatSearchTheme.searchBarY(height);

        EditBox box = new EditBox(
                mc.font, 4, y, width - 8, ChatSearchTheme.SEARCH_BAR_HEIGHT,
                Component.literal("Search chat..."));
        box.setHint(Component.literal("Search chat... (Ctrl+F)"));
        box.setMaxLength(SEARCH_INPUT_MAX_LEN);
        box.setResponder(value -> sbe$onQueryChanged(search, value));
        box.setBordered(false);
        box.setCanLoseFocus(true);

        if (sbe$pendingQuery != null) {
            box.setValue(sbe$pendingQuery);
            sbe$pendingQuery = null;
        } else {
            box.setValue(search.getQuery());
        }
        return box;
    }

    @Unique
    private void sbe$toggleSearch() {
        ChatSearchController search = ChatFeatureState.get().search();
        if (!search.isActive()) {
            sbe$openSearch();
            return;
        }
        if (!SkyblockEnhancementsConfig.alwaysShowChatSearch) {
            sbe$closeSearch();
            return;
        }
        sbe$pingPongFocus();
    }

    @Unique
    private void sbe$openSearch() {
        ChatFeatureState.get().search().setActive(true);
        sbe$pendingQuery = "";
        rebuildWidgets();
        sbe$focusSearchBox();
    }

    @Unique
    private void sbe$closeSearch() {
        ChatSearchController search = ChatFeatureState.get().search();
        search.setActive(false);
        search.setQuery("");
        sbe$pendingQuery = null;
        sbe$refreshChat();
        rebuildWidgets();
        input.setCanLoseFocus(false);
        sbe$focusInput();
    }

    @Unique
    private void sbe$onQueryChanged(ChatSearchController search, String query) {
        search.setQuery(query);
        sbe$refreshChat();
    }

    @Unique
    private void sbe$refreshChat() {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.getChat().resetChatScroll();
        ((SBEChatAccess) mc.gui.getChat()).sbe$refreshMessages();
    }

    // ── Focus helpers ────────────────────────────────────────────────────

    @Unique
    private void sbe$focusSearchBox() {
        if (sbe$searchBox == null) return;
        setFocused(sbe$searchBox);
        sbe$searchBox.setFocused(true);
        input.setFocused(false);
    }

    @Unique
    private void sbe$focusInput() {
        setFocused(input);
        input.setFocused(true);
        if (sbe$searchBox != null) sbe$searchBox.setFocused(false);
    }

    @Unique
    private void sbe$pingPongFocus() {
        if (sbe$searchBox == null) return;
        if (sbe$searchBox.isFocused()) sbe$focusInput();
        else sbe$focusSearchBox();
    }
}