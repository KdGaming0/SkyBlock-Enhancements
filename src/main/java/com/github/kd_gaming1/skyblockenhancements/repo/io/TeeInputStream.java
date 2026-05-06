package com.github.kd_gaming1.skyblockenhancements.repo.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/**
 * An {@link InputStream} that forwards every byte read from a wrapped stream
 * to an {@link OutputStream}. Useful for saving a downloaded archive to disk
 * while simultaneously parsing its contents, avoiding a redundant second read.
 *
 * <p>Closing this stream closes only the wrapped input; the branch output is
 * left open so callers can close it independently (e.g. in a try-with-resources).
 * The close operation is idempotent — calling it multiple times is safe.
 */
public final class TeeInputStream extends InputStream {

    private final InputStream in;
    private final OutputStream branch;
    private boolean closed;

    public TeeInputStream(InputStream in, OutputStream branch) {
        this.in = Objects.requireNonNull(in);
        this.branch = Objects.requireNonNull(branch);
    }

    @Override
    public int read() throws IOException {
        int b = in.read();
        if (b != -1) {
            branch.write(b);
        }
        return b;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        int n = in.read(buf, off, len);
        if (n != -1) {
            branch.write(buf, off, n);
        }
        return n;
    }

    @Override
    public long skip(long n) throws IOException {
        return in.skip(n);
    }

    @Override
    public int available() throws IOException {
        return in.available();
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        in.close();
    }
}
