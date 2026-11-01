package io.github.arbiter.cli;

import io.github.arbiter.api.config.ArbiterHome;
import io.github.arbiter.api.config.ConfigKeys;
import io.github.arbiter.api.config.ArbiterKeys;

import io.github.arbiter.client.config.ApiClientConfig;
import io.github.arbiter.client.config.HttpClientConfig;
import io.github.arbiter.client.config.HttpTransportConfig;
import io.github.arbiter.client.config.TlsClientConfig;
import io.github.arbiter.client.config.TransportConfig;
import io.github.arbiter.client.config.UnixSocketTransportConfig;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;

public record BootstrapSettings(String server, String configPath,
                                ApiClientConfig apiClientConfig,
                                TransportConfig transportConfig) {
    /**
     * Compatibility accessor for HTTP-only callers and tests.
     */
    public HttpClientConfig httpConfig() {
        if (!(transportConfig instanceof HttpTransportConfig http)) {
            throw new IllegalStateException("Active transport is not HTTP");
        }
        return new HttpClientConfig(http.baseUri(), apiClientConfig.apiPrefix(),
                http.connectTimeout(), http.readTimeout(), http.poolAcquireTimeout(),
                http.poolPermits(), apiClientConfig.maxRetries(),
                apiClientConfig.retryBaseDelay(), http.tls());
    }

    public HttpTransportConfig httpTransportConfig() {
        if (!(transportConfig instanceof HttpTransportConfig http)) {
            throw new IllegalStateException("Active transport is not HTTP");
        }
        return http;
    }

    public UnixSocketTransportConfig unixSocketTransportConfig() {
        if (!(transportConfig instanceof UnixSocketTransportConfig unix)) {
            throw new IllegalStateException("Active transport is not Unix socket");
        }
        return unix;
    }

    public static BootstrapSettings resolve(Map<String, String> environment,
                                            Path workingDirectory) throws IOException {
        return resolve(null, environment, workingDirectory);
    }

    /**
     * Resolves configuration from an already-parsed {@code --config} value.
     * This method must never rescan the raw argument array.
     */
    public static BootstrapSettings resolve(String cliConfig,
                                            Map<String, String> environment,
                                            Path workingDirectory) throws IOException {
        String explicitConfig = blankToNull(cliConfig);
        Properties properties = new Properties();
        Path configPath = null;

        // Shared distribution settings load first; every other file overrides them.
        Path commonConfig = distributionFile("common.properties");
        if (commonConfig != null) {
            load(properties, commonConfig);
            configPath = commonConfig;
        }

        if (explicitConfig != null) {
            configPath = Path.of(explicitConfig).toAbsolutePath().normalize();
            if (!Files.isRegularFile(configPath)) {
                throw new IllegalArgumentException("Configuration file does not exist: "
                        + configPath);
            }
            load(properties, configPath);
        } else {
            Path distributionConfig = distributionFile("cli.properties");
            if (distributionConfig != null) {
                load(properties, distributionConfig);
                configPath = distributionConfig;
            }
            Path userConfig = UserPaths.configDirectory().resolve("config");
            if (Files.isRegularFile(userConfig)) {
                load(properties, userConfig);
                configPath = userConfig;
            }
            Path projectConfig = findProjectConfig(workingDirectory);
            if (projectConfig != null) {
                load(properties, projectConfig);
                configPath = projectConfig;
            }
        }

        String transport = firstNonBlank(environment.get(ArbiterKeys.Env.TRANSPORT),
                properties.getProperty(ConfigKeys.Common.TRANSPORT), null);
        boolean unix = transport != null && "unix".equalsIgnoreCase(transport);
        if (transport != null && !"unix".equalsIgnoreCase(transport)
                && !"http".equalsIgnoreCase(transport)
                && !"both".equalsIgnoreCase(transport)) {
            throw new IllegalArgumentException("ARBITER_TRANSPORT must be http, unix, or both");
        }

        String prefix = properties.getProperty(ConfigKeys.Common.API_PREFIX, "/api/v1");
        ApiClientConfig client = new ApiClientConfig(prefix,
                nonNegativeInt(properties, ConfigKeys.Client.RETRY_MAX, 2),
                duration(properties, ConfigKeys.Client.RETRY_BASE_DELAY_MS, 150));
        if (unix) {
            // The socket path is fixed at <arbiter.home>/run/server.sock.
            Path socketPath = ArbiterHome.defaultSocketPath();
            if (socketPath == null) {
                throw new IllegalArgumentException(
                        "unix transport requires arbiter.home;"
                        + " run through bin/arbiter or bin/arbiter.bat");
            }
            if (TlsClientConfig.fromSources(environment, properties).enabled()) {
                throw new IllegalArgumentException(
                        "TLS is only supported for http transport");
            }
            return new BootstrapSettings("unix:" + socketPath,
                    configPath == null ? explicitConfig : configPath.toString(), client,
                    new UnixSocketTransportConfig(socketPath,
                            duration(properties, ConfigKeys.Client.READ_TIMEOUT_MS, 10000)));
        }

        // The endpoint always comes from the shared host/port settings.
        String httpServer = derivedServer(properties);
        HttpTransportConfig http = new HttpTransportConfig(
                URI.create(httpServer),
                duration(properties, ConfigKeys.Client.CONNECT_TIMEOUT_MS, 3000),
                duration(properties, ConfigKeys.Client.READ_TIMEOUT_MS, 10000),
                duration(properties, ConfigKeys.Client.POOL_ACQUIRE_TIMEOUT_MS, 2000),
                positiveInt(properties, ConfigKeys.Client.POOL_PERMITS, 4),
                TlsClientConfig.fromSources(environment, properties));
        return new BootstrapSettings(httpServer,
                configPath == null ? explicitConfig : configPath.toString(), client, http);
    }

    /**
     * Builds the server URL from the client host when set, otherwise the
     * shared server host. The port always comes from the shared settings.
     */
    private static String derivedServer(Properties properties) {
        String host = properties.getProperty(ConfigKeys.Client.HOST);
        if (host == null || host.isBlank()) {
            host = properties.getProperty(ConfigKeys.Common.SERVER_HOST);
        }
        if (host == null || host.isBlank()) {
            host = "127.0.0.1";
        }
        String portValue = properties.getProperty(ConfigKeys.Common.SERVER_PORT);
        if (portValue == null || portValue.isBlank()) {
            portValue = "8080";
        }
        int port;
        try {
            port = Integer.parseInt(portValue.trim());
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("port must be a number: " + portValue, invalid);
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        return "http://" + host.trim() + ":" + port;
    }

    /** A distribution config file, or {@code null} when it is absent. */
    private static Path distributionFile(String name) {
        Path home = ArbiterHome.root();
        if (home == null) {
            return null;
        }
        Path config = home.resolve("conf").resolve(name);
        return Files.isRegularFile(config) ? config : null;
    }

    private static Path findProjectConfig(Path workingDirectory) {
        Path current = workingDirectory.toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(".arbiterrc");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
    }

    private static void load(Properties properties, Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
    }

    private static Duration duration(Properties properties, String name, long fallback) {
        String raw = properties.getProperty(name);
        long value = raw == null || raw.isBlank() ? fallback : Long.parseLong(raw);
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return Duration.ofMillis(value);
    }

    private static int positiveInt(Properties properties, String name, int fallback) {
        String raw = properties.getProperty(name);
        int value = raw == null || raw.isBlank() ? fallback : Integer.parseInt(raw);
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static int nonNegativeInt(Properties properties, String name, int fallback) {
        String raw = properties.getProperty(name);
        int value = raw == null || raw.isBlank() ? fallback : Integer.parseInt(raw);
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
