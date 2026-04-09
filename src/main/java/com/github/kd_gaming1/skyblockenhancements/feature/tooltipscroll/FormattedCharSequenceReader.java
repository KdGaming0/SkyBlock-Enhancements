package com.github.kd_gaming1.skyblockenhancements.feature.tooltipscroll;

import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import org.jspecify.annotations.NonNull;

/**
 * Reads a {@link FormattedCharSequence} into a plain {@link String}.
 *
 * <p>Adapted from Provismet's tooltip-scroll mod (MIT license).
 * See THIRD_PARTY_LICENSES.md for details.</p>
 */
public final class FormattedCharSequenceReader {

    private FormattedCharSequenceReader() {}

    /**
     * Visits every character in the sequence and concatenates them into
     * a plain string, discarding style information.
     */
    public static String read(FormattedCharSequence text) {
        ReaderSink sink = new ReaderSink();
        text.accept(sink);
        return sink.result.toString();
    }

    private static class ReaderSink implements FormattedCharSink {
        final StringBuilder result = new StringBuilder();

        @Override
        public boolean accept(int index, @NonNull Style style, int codePoint) {
            result.append((char) codePoint);
            return true;
        }
    }
}