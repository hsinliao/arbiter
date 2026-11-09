package io.github.arbiter.cli;

import io.github.arbiter.api.config.ArbiterKeys;
import io.github.arbiter.client.config.HttpTransportConfig;
import io.github.arbiter.client.config.UnixSocketTransportConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BootstrapSettingsTest {
    @TempDir
    Path temporaryDirectory;

    private String originalHome;

    @BeforeEach
    void isolateUserConfig() {
        originalHome = System.getProperty(ArbiterKeys.SystemProperty.USER_HOME);
        System.setProperty(ArbiterKeys.SystemProperty.USER_HOME, temporaryDirectory.resolve("user").toString());
    }

    @AfterEach
    void restoreUserConfig() {
        if (originalHome == null) {
            System.clearProperty(ArbiterKeys.SystemProperty.USER_HOME);
        } else {
            System.setProperty(ArbiterKeys.SystemProperty.USER_HOME, originalHome);
        }
    }

    @Test
    void derivesUnixSocketFromArbiterHome() throws Exception {
        String previous = System.getProperty(ArbiterKeys.SystemProperty.HOME);
        System.setProperty(ArbiterKeys.SystemProperty.HOME, temporaryDirectory.toString());
        try {
            BootstrapSettings settings = BootstrapSettings.resolve(null,
                    Map.of(ArbiterKeys.Env.TRANSPORT, "unix"), temporaryDirectory);

            assertEquals("unix:" + temporaryDirectory.resolve("run").resolve("server.sock")
                    .toAbsolutePath().normalize(), settings.server());
            assertTrue(settings.transportConfig()
                instanceof UnixSocketTransportConfig);
        } finally {
            if (previous == null) {
                System.clearProperty(ArbiterKeys.SystemProperty.HOME);
            } else {
                System.setProperty(ArbiterKeys.SystemProperty.HOME, previous);
            }
        }
    }

    @Test
    void unixTransportWithoutArbiterHomeFails() {
        String previous = System.getProperty(ArbiterKeys.SystemProperty.HOME);
        System.clearProperty(ArbiterKeys.SystemProperty.HOME);
        try {
            assertThrows(IllegalArgumentException.class, () -> BootstrapSettings.resolve(null, Map.of(ArbiterKeys.Env.TRANSPORT, "unix"), temporaryDirectory));
        } finally {
            if (previous == null) {
                System.clearProperty(ArbiterKeys.SystemProperty.HOME);
            } else {
                System.setProperty(ArbiterKeys.SystemProperty.HOME, previous);
            }
        }
    }

    @Test
    void explicitConfigOverridesProjectAndUserConfig() throws Exception {
        Path userConfig = Files.createDirectories(
                temporaryDirectory.resolve("user/.config/arbiter")).resolve("config");
        Files.writeString(userConfig, "arbiter.api.prefix=/user\n");
        Files.writeString(temporaryDirectory.resolve(".arbiterrc"),
                "arbiter.api.prefix=/project\n");
        Path explicit = temporaryDirectory.resolve("explicit.properties");
        Files.writeString(explicit, "arbiter.api.prefix=/file\n");

        BootstrapSettings settings = BootstrapSettings.resolve(explicit.toString(), Map.of(), temporaryDirectory);

        assertEquals("/file", settings.apiClientConfig().apiPrefix());
        assertEquals(explicit.toAbsolutePath().normalize(), Path.of(settings.configPath()));
    }

    @Test
    void arbiterServerEnvironmentVariableIsIgnored() throws Exception {
        BootstrapSettings settings = BootstrapSettings.resolve(null, Map.of("ARBITER_SERVER", "http://env:8080"),
                temporaryDirectory);

        assertEquals("http://127.0.0.1:8080", settings.server());
    }

    @Test
    void projectConfigOverridesUserConfig() throws Exception {
        Path userConfig = Files.createDirectories(
                temporaryDirectory.resolve("user/.config/arbiter")).resolve("config");
        Files.writeString(userConfig, "arbiter.api.prefix=/user\n");
        Files.writeString(temporaryDirectory.resolve(".arbiterrc"),
                "arbiter.api.prefix=/project\n");

        assertEquals("/project", BootstrapSettings.resolve(null, Map.of(), temporaryDirectory)
                .apiClientConfig().apiPrefix());
    }

    @Test
    void commonConfigLoadsBeforeCliConfig() throws Exception {
        String previous = System.getProperty(ArbiterKeys.SystemProperty.HOME);
        Path home = Files.createDirectories(temporaryDirectory.resolve("dist"));
        Path conf = Files.createDirectories(home.resolve("conf"));
        Files.writeString(conf.resolve("common.properties"), "arbiter.api.prefix=/shared\n");
        Files.writeString(conf.resolve("cli.properties"), "arbiter.api.prefix=/cli\n");
        System.setProperty(ArbiterKeys.SystemProperty.HOME, home.toString());
        try {
            assertEquals("/cli", BootstrapSettings.resolve(null, Map.of(), temporaryDirectory)
                    .apiClientConfig().apiPrefix());

            Files.delete(conf.resolve("cli.properties"));
            assertEquals("/shared", BootstrapSettings.resolve(null, Map.of(), temporaryDirectory)
                    .apiClientConfig().apiPrefix());
        } finally {
            if (previous == null) {
                System.clearProperty(ArbiterKeys.SystemProperty.HOME);
            } else {
                System.setProperty(ArbiterKeys.SystemProperty.HOME, previous);
            }
        }
    }

    @Test
    void derivesServerFromSharedHostAndPort() throws Exception {
        String previous = System.getProperty(ArbiterKeys.SystemProperty.HOME);
        Path home = Files.createDirectories(temporaryDirectory.resolve("dist"));
        Path conf = Files.createDirectories(home.resolve("conf"));
        Files.writeString(conf.resolve("common.properties"),
                "arbiter.server.host=127.0.0.1\narbiter.server.port=9091\n");
        System.setProperty(ArbiterKeys.SystemProperty.HOME, home.toString());
        try {
            assertEquals("http://127.0.0.1:9091", BootstrapSettings.resolve(null, Map.of(), temporaryDirectory).server());
        } finally {
            if (previous == null) {
                System.clearProperty(ArbiterKeys.SystemProperty.HOME);
            } else {
                System.setProperty(ArbiterKeys.SystemProperty.HOME, previous);
            }
        }
    }

    @Test
    void clientHostOverridesSharedServerHost() throws Exception {
        String previous = System.getProperty(ArbiterKeys.SystemProperty.HOME);
        Path home = Files.createDirectories(temporaryDirectory.resolve("dist"));
        Path conf = Files.createDirectories(home.resolve("conf"));
        Files.writeString(conf.resolve("common.properties"),
                "arbiter.server.host=127.0.0.1\narbiter.server.port=9091\n");
        Files.writeString(conf.resolve("cli.properties"),
                "arbiter.client.host=192.168.1.10\n");
        System.setProperty(ArbiterKeys.SystemProperty.HOME, home.toString());
        try {
            assertEquals("http://192.168.1.10:9091", BootstrapSettings.resolve(
                    null, Map.of(), temporaryDirectory).server());
        } finally {
            if (previous == null) {
                System.clearProperty(ArbiterKeys.SystemProperty.HOME);
            } else {
                System.setProperty(ArbiterKeys.SystemProperty.HOME, previous);
            }
        }
    }

    @Test
    void sharedTransportBothUsesHttp() throws Exception {
        String previous = System.getProperty(ArbiterKeys.SystemProperty.HOME);
        Path home = Files.createDirectories(temporaryDirectory.resolve("dist"));
        Path conf = Files.createDirectories(home.resolve("conf"));
        Files.writeString(conf.resolve("common.properties"), "arbiter.transport=both\n");
        System.setProperty(ArbiterKeys.SystemProperty.HOME, home.toString());
        try {
            BootstrapSettings settings = BootstrapSettings.resolve(null, Map.of(), temporaryDirectory);

            assertTrue(settings.transportConfig()
                instanceof HttpTransportConfig);
        } finally {
            if (previous == null) {
                System.clearProperty(ArbiterKeys.SystemProperty.HOME);
            } else {
                System.setProperty(ArbiterKeys.SystemProperty.HOME, previous);
            }
        }
    }

    @Test
    void distributionConfigIsLoadedAndOverriddenByProjectConfig() throws Exception {
        String previous = System.getProperty(ArbiterKeys.SystemProperty.HOME);
        Path home = Files.createDirectories(temporaryDirectory.resolve("dist"));
        Path conf = Files.createDirectories(home.resolve("conf"));
        Files.writeString(conf.resolve("cli.properties"), "arbiter.api.prefix=/dist\n");
        System.setProperty(ArbiterKeys.SystemProperty.HOME, home.toString());
        try {
            assertEquals("/dist", BootstrapSettings.resolve(null, Map.of(), temporaryDirectory)
                    .apiClientConfig().apiPrefix());

            Files.writeString(temporaryDirectory.resolve(".arbiterrc"),
                    "arbiter.api.prefix=/project\n");
            assertEquals("/project", BootstrapSettings.resolve(null, Map.of(), temporaryDirectory)
                    .apiClientConfig().apiPrefix());
        } finally {
            if (previous == null) {
                System.clearProperty(ArbiterKeys.SystemProperty.HOME);
            } else {
                System.setProperty(ArbiterKeys.SystemProperty.HOME, previous);
            }
        }
    }
}
