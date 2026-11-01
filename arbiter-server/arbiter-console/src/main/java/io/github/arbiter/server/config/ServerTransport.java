package io.github.arbiter.server.config;

import java.nio.file.Path;

/**
 * Selects HTTP, Unix-domain socket, or both listeners. The HTTP listener is
 * optional so a UDS-only deployment has no network surface at all.
 */
public record ServerTransport(Mode mode, Path socketPath) {
    public enum Mode {
        HTTP,
        UNIX,
        BOTH
    }

    public ServerTransport {
        if (mode == null) {
            throw new IllegalArgumentException("transport mode must not be null");
        }
        if (mode == Mode.UNIX || mode == Mode.BOTH) {
            if (socketPath == null || socketPath.toString().isBlank()) {
                throw new IllegalArgumentException(
                        "unix transport requires arbiter.home; start the server"
                        + " through bin/server-start or bin/server-start.bat");
            }
            socketPath = socketPath.toAbsolutePath().normalize();
        }
    }

    public static ServerTransport http() {
        return new ServerTransport(Mode.HTTP, null);
    }

    public boolean httpEnabled() {
        return mode == Mode.HTTP || mode == Mode.BOTH;
    }

    public boolean unixEnabled() {
        return mode == Mode.UNIX || mode == Mode.BOTH;
    }
}
