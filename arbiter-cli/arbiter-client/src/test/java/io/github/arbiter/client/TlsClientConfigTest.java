package io.github.arbiter.client;

import io.github.arbiter.api.config.ArbiterKeys;
import io.github.arbiter.api.config.ConfigKeys;

import io.github.arbiter.client.config.HttpClientConfig;
import io.github.arbiter.client.config.TlsClientConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TlsClientConfigTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void disabledByDefault() {
        assertFalse(TlsClientConfig.fromSources(Map.of(), new Properties()).enabled());
    }

    @Test
    void pathsInferTlsButPasswordsRemainEnvironmentOnly() throws Exception {
        Path trustStore = Files.writeString(temporaryDirectory.resolve("client-trust.p12"), "");
        Properties properties = new Properties();
        properties.setProperty(ConfigKeys.Client.Tls.TRUSTSTORE, trustStore.toString());

        assertThrows(IllegalArgumentException.class,
                () -> TlsClientConfig.fromSources(Map.of(), properties));

        TlsClientConfig settings = TlsClientConfig.fromSources(
                Map.of(ArbiterKeys.Env.TLS_TRUSTSTORE_PASSWORD, "changeit"), properties);
        assertTrue(settings.enabled());
    }

    @Test
    void tlsRequiresHttpsBaseUrl() {
        TlsClientConfig tls = new TlsClientConfig(true, null, null, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> new HttpClientConfig(
                URI.create("http://localhost:8080"), "/api/v1",
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1),
                4, 2, Duration.ofMillis(150), tls));
    }
}
