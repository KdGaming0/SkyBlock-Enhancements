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
 *
 * <p>Escape behaviour: first press clears the query (if non-empty); second press closes the bar
 * (unless {@code alwaysShowChatSearch} is on, in which case the bar stays open but cleared).
 */
@Mixin(ChatScreen.class)
public abstract class ChatSearchScreenMixin extends Screen {

    @Shadow protected EditBox input;

    @Unique private EditBox sbe$searchBox;
    @Unique private String sbe$pendingQuery;

    // Semi-transparent dark background, same style as the vanilla chat input fill.
    // vanilla uses minecraft.options.getBackgroundColor(Integer.MIN_VALUE) which is ~0x90000000.
    // We use a slightly lighter value so the bar feels less heavy.
    @Unique private static final int SBE_SEARCH_BG = 0x60000000;

    protected ChatSearchScreenMixin(Component title) {
        super(title);
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Inject(method = "init", at = @At("TAIL"))
    private void sbe$initSearchBar(CallbackInfo ci) {
        if (!SkyblockEnhancementsConfig.enableChatSearch) return;

        // Auto-open when "always show" is configured
        if (SkyblockEnhancementsConfig.alwaysShowChatSearch && !ChatSearchState.isActive()) {
            ChatSearchState.setActive(true);
        }

        if (!ChatSearchState.isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        int y = ChatSearchLayout.searchBarY(height);

        // Use plain vanilla EditBox with no border — identical look to the chat input itself.
        sbe$searchBox = new EditBox(
                mc.font,
                4, y, width - 8, ChatSearchLayout.SEARCH_BAR_HEIGHT,
                Component.literal("Search chat..."));
        sbe$searchBox.setMaxLength(128);
        sbe$searchBox.setResponder(this::sbe$onQueryChanged);
        sbe$searchBox.setBordered(false);       // removes EditBox's own opaque background
        sbe$searchBox.setCanLoseFocus(true);    // allow clicking vanilla input to steal focus

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
        if (!SkyblockEnhancementsConfig.alwaysShowChatSearch) {
            mc.schedule(() -> setFocused(sbe$searchBox));
        }
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

    /** Draw the match-count overlay after widgets (so it appears on top of the text). */
    @Inject(method = "render", at = @At("TAIL"))
    private void sbe$renderMatchCount(GuiGraphics graphics, int mouseX, int mouseY, float delta,
                                      CallbackInfo ci) {
        if (!SkyblockEnhancementsConfig.enableChatSearch) return;
        if (!ChatSearchState.isActive() || sbe$searchBox == null) return;

        if (ChatSearchState.isFiltering()) {
            SBEChatAccess access = (SBEChatAccess) Minecraft.getInstance().gui.getChat();
            int visible = access.sbe$getTrimmedMessages().size();
            int total   = access.sbe$getAllMessages().size();
            String info = visible + "/" + total;
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
        if (ChatSearchState.isActive() && !SkyblockEnhancementsConfig.alwaysShowChatSearch) {
            sbe$closeSearch();
        } else if (!ChatSearchState.isActive()) {
            sbe$openSearch();
        } else {
            if (sbe$searchBox != null) {
                Minecraft.getInstance().schedule(() -> setFocused(sbe$searchBox));
            }
        }
    }

    @Unique
    private void sbe$openSearch() {
        ChatSearchState.setActive(true);
        sbe$pendingQuery = "";
        rebuildWidgets();
    }

    @Unique
    private void sbe$closeSearch() {
        ChatSearchState.setActive(false);
        ChatSearchState.setQuery("");
        sbe$pendingQuery = null;
        sbe$refreshChat();
        rebuildWidgets();
        input.setCanLoseFocus(false);
        Minecraft.getInstance().schedule(() -> setFocused(input));
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