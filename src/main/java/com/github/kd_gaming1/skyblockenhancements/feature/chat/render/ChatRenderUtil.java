package com.github.kd_gaming1.skyblockenhancements.feature.chat.render;

import com.github.kd_gaming1.skyblockenhancements.access.SBEChatAccess;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the currently-rendering ChatComponent so that inner-class mixins can
 * reach it without needing a captured {@code this$0} reference.
 */
public final class ChatRenderUtil {
    public static @Nullable SBEChatAccess activeChatAccess;
    private ChatRenderUtil() {}
}