package io.github.arbiter.server;

import io.github.arbiter.api.config.ArbiterKeys;
import io.github.arbiter.api.config.ConfigKeys;

import io.github.arbiter.server.config.ServerConfig;
import io.github.arbiter.server.config.ServerTransport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerConfigTest {
    @Test
    void readsValuesFromProperties() {
        Properties properties = new Properties();
        properties.setProperty(ConfigKeys.Common.SERVER_HOST, "0.0.0.0");
        properties.setProperty(ConfigKeys.Common.SERVER_PORT, "9090");
        properties.setProperty(ConfigKeys.Common.API_PREFIX, "/custom");
        properties.setProperty(ConfigKeys.Server.TOKEN_TTL_SECONDS, "120");
        properties.setProperty(ConfigKeys.Server.MAX_REQUEST_BYTES, "4096");
        properties.setProperty(ConfigKeys.Server.LOGIN_MAX_FAILURES, "3");
        properties.setProperty(ConfigKeys.Server.LOGIN_WINDOW_SECONDS, "30");

        ServerConfig config = ServerConfig.fromProperties(properties);

        assertEquals("0.0.0.0", config.host());
        assertEquals(9090, config.port());
        assertEquals("/custom", config.apiPrefix());
        assertEquals(120, config.tokenTtl().toSeconds());
        assertEquals(4096, config.maxRequestBytes());
        assertEquals(3, config.loginMaxFailures());
        assertEquals(30, config.loginWindow().toSeconds());
    }

    @Test
    void derivesUnixSocketFromArbiterHome(@TempDir Path home) {
        String previous = System.getProperty(ArbiterKeys.SystemProperty.HOME);
        System.setProperty(ArbiterKeys.SystemProperty.HOME, home.toString());
        try {
            Properties properties = new Properties();
            properties.setProperty(ConfigKeys.Common.TRANSPORT, "both");

            ServerConfig config = ServerConfig.fromProperties(properties);

            assertEquals(ServerTransport.Mode.BOTH, config.transport().mode());
            assertEquals(home.resolve("run").resolve("server.sock").toAbsolutePath().normalize(),
                    config.transport().socketPath());
        } finally {
            restoreArbiterHome(previous);
        }
    }

    @Test
    void unixTransportWithoutArbiterHomeFails() {
        String previous = System.getProperty(ArbiterKeys.SystemProperty.HOME);
        System.clearProperty(ArbiterKeys.SystemProperty.HOME);
        try {
            Properties properties = new Properties();
            properties.setProperty(ConfigKeys.Common.TRANSPORT, "unix");

            assertThrows(IllegalArgumentException.class,
                    () -> ServerConfig.fromProperties(properties));
        } finally {
            restoreArbiterHome(previous);
        }
    }

    @Test
    void httpTransportHasNoSocketPath() {
        ServerConfig config = ServerConfig.fromProperties(new Properties());

        assertEquals(ServerTransport.Mode.HTTP, config.transport().mode());
        assertNull(config.transport().socketPath());
    }

    private static void restoreArbiterHome(String previous) {
        if (previous == null) {
            System.clearProperty(ArbiterKeys.SystemProperty.HOME);
        } else {
            System.setProperty(ArbiterKeys.SystemProperty.HOME, previous);
        }
    }
}
