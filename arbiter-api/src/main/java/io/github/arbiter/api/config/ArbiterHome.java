package io.github.arbiter.api.config;

import java.nio.file.Path;

/**
 * Resolves installation-relative paths from the launcher-provided
 * {@code arbiter.home} system property. Both the CLI and the server use the
 * same derivation, so a Unix-domain socket needs no explicit path when both
 * processes come from the same distribution.
 */
public final class ArbiterHome {
    private static final String RUN_DIRECTORY = "run";
    private static final String SERVER_SOCKET = "server.sock";

    private ArbiterHome() {
    }

    /**
     * @return the distribution root, or {@code null} when the launcher did not
     *         provide {@code arbiter.home}
     */
    public static Path root() {
        String home = System.getProperty(ArbiterKeys.SystemProperty.HOME);
        if (home == null || home.isBlank()) {
            return null;
        }
        return Path.of(home).toAbsolutePath().normalize();
    }

    /**
     * @return the default Unix-domain socket path ({@code <arbiter.home>/run/server.sock}),
     *         or {@code null} when {@code arbiter.home} is unavailable
     */
    public static Path defaultSocketPath() {
        Path root = root();
        return root == null ? null : root.resolve(RUN_DIRECTORY).resolve(SERVER_SOCKET);
    }

    /**
     * @return the fixed TLS material directory ({@code <arbiter.home>/conf/security}),
     *         or {@code null} when {@code arbiter.home} is unavailable
     */
    public static Path securityDirectory() {
        Path root = root();
        return root == null ? null : root.resolve("conf").resolve("security");
    }
}
