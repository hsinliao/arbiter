package io.github.arbiter.server.config;

import io.github.arbiter.api.config.ArbiterHome;
import io.github.arbiter.api.config.ConfigKeys;

import io.github.arbiter.server.tls.TlsSettings;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

public record ServerConfig(
        String host,
        int port,
        String apiPrefix,
        Duration tokenTtl,
        int maxRequestBytes,
        int loginMaxFailures,
        Duration loginWindow,
        TlsSettings tls,
        ServerTransport transport
) {
    public ServerConfig {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Server host must not be blank");
        }
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("Server port must be between 0 and 65535");
        }
        if (apiPrefix == null || !apiPrefix.startsWith("/")) {
            throw new IllegalArgumentException("API prefix must start with '/'");
        }
        apiPrefix = apiPrefix.replaceAll("/+$", "");
        if (apiPrefix.isEmpty()) {
            apiPrefix = "/";
        }
        requirePositive(tokenTtl, "tokenTtl");
        if (maxRequestBytes < 1024) {
            throw new IllegalArgumentException("maxRequestBytes must be at least 1024");
        }
        if (loginMaxFailures < 1) {
            throw new IllegalArgumentException("loginMaxFailures must be positive");
        }
        requirePositive(loginWindow, "loginWindow");
        if (tls == null) {
            throw new IllegalArgumentException("tls must not be null");
        }
        if (transport == null) {
            throw new IllegalArgumentException("transport must not be null");
        }
        if (transport.mode() == ServerTransport.Mode.UNIX && tls.enabled()) {
            throw new IllegalArgumentException(
                    "TLS is only supported for HTTP transport");
        }
    }

    public ServerConfig(String host, int port, String apiPrefix, Duration tokenTtl,
                        int maxRequestBytes, int loginMaxFailures,
                        Duration loginWindow) {
        this(host, port, apiPrefix, tokenTtl, maxRequestBytes,
                loginMaxFailures, loginWindow, TlsSettings.disabled(),
                ServerTransport.http());
    }

    public ServerConfig(String host, int port, String apiPrefix, Duration tokenTtl,
                        int maxRequestBytes, int loginMaxFailures,
                        Duration loginWindow, TlsSettings tls) {
        this(host, port, apiPrefix, tokenTtl, maxRequestBytes,
                loginMaxFailures, loginWindow, tls, ServerTransport.http());
    }

    public static ServerConfig fromProperties(Properties properties) {
        return new ServerConfig(
                value(properties, ConfigKeys.Common.SERVER_HOST, "127.0.0.1"),
                positiveInt(properties, ConfigKeys.Common.SERVER_PORT, 8080),
                value(properties, ConfigKeys.Common.API_PREFIX, "/api/v1"),
                Duration.ofSeconds(positiveLong(properties, ConfigKeys.Server.TOKEN_TTL_SECONDS, 3600)),
                positiveInt(properties, ConfigKeys.Server.MAX_REQUEST_BYTES, 65_536),
                positiveInt(properties, ConfigKeys.Server.LOGIN_MAX_FAILURES, 5),
                Duration.ofSeconds(positiveLong(properties, ConfigKeys.Server.LOGIN_WINDOW_SECONDS, 60)),
                TlsSettings.fromProperties(properties),
                transport(properties)
        );
    }

    private static ServerTransport transport(Properties properties) {
        ServerTransport.Mode mode = ServerTransport.parseMode(
                value(properties, ConfigKeys.Common.TRANSPORT, "http"));
        // The socket path is fixed at <arbiter.home>/run/server.sock; only the
        // transport mode is configurable.
        Path socketPath = mode == ServerTransport.Mode.HTTP
                ? null
                : ArbiterHome.defaultSocketPath();
        return new ServerTransport(mode, socketPath);
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static int positiveInt(Properties properties, String propertyKey, int fallback) {
        String value = properties.getProperty(propertyKey);
        int parsed = value == null || value.isBlank() ? fallback : Integer.parseInt(value);
        if (parsed < 1) {
            throw new IllegalArgumentException(propertyKey + " must be positive");
        }
        return parsed;
    }

    private static long positiveLong(Properties properties, String propertyKey, long fallback) {
        String value = properties.getProperty(propertyKey);
        long parsed = value == null || value.isBlank() ? fallback : Long.parseLong(value);
        if (parsed < 1) {
            throw new IllegalArgumentException(propertyKey + " must be positive");
        }
        return parsed;
    }

    private static String value(Properties properties, String propertyKey, String fallback) {
        String value = properties.getProperty(propertyKey);
        return value == null || value.isBlank() ? fallback : value;
    }
}
