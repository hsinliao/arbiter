package io.github.arbiter.cli;

import io.github.arbiter.api.config.ArbiterKeys;

import java.nio.file.Path;

/**
 * Resolves the user configuration root without relying on a potentially
 * incomplete JVM user.home value in restricted Windows environments.
 */
final class UserPaths {
    static final String HOME_OVERRIDE = ArbiterKeys.SystemProperty.USER_HOME;

    private UserPaths() {
    }

    static Path userHome() {
        String override = System.getProperty(HOME_OVERRIDE);
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        String environmentHome = System.getenv("HOME");
        if (environmentHome == null || environmentHome.isBlank()) {
            environmentHome = System.getenv("USERPROFILE");
        }
        if (environmentHome == null || environmentHome.isBlank()) {
            environmentHome = System.getProperty("user.home");
        }
        Path resolved = Path.of(environmentHome).toAbsolutePath().normalize();
        if (resolved.getParent() == null && !resolved.equals(Path.of(environmentHome))) {
            return Path.of(System.getProperty("user.dir")).toAbsolutePath();
        }
        return resolved;
    }

    static Path configDirectory() {
        return userHome().resolve(".config").resolve("arbiter");
    }
}
