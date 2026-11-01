package io.github.arbiter.client.config;

import io.github.arbiter.api.config.ArbiterKeys;
import io.github.arbiter.api.config.ConfigKeys;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/**
 * Client-side TLS/mTLS settings. Certificate paths may come from the user
 * configuration; passwords are read only from environment variables.
 */
public record TlsClientConfig(
        boolean enabled,
        Path trustStore,
        String trustStoreType,
        char[] trustStorePassword,
        Path keyStore,
        String keyStoreType,
        char[] keyStorePassword
) {
    public TlsClientConfig {
        trustStoreType = normalizeType(trustStoreType);
        keyStoreType = normalizeType(keyStoreType);
        trustStorePassword = trustStorePassword == null
                ? new char[0] : trustStorePassword.clone();
        keyStorePassword = keyStorePassword == null
                ? new char[0] : keyStorePassword.clone();
        if (enabled) {
            if (trustStore != null && !Files.isRegularFile(trustStore)) {
                throw new IllegalArgumentException(
                        "TLS trustStore does not exist: " + trustStore);
            }
            if (keyStore != null && !Files.isRegularFile(keyStore)) {
                throw new IllegalArgumentException(
                        "TLS keyStore does not exist: " + keyStore);
            }
            if (trustStore != null && trustStorePassword.length == 0) {
                throw new IllegalArgumentException(
                        "TLS trustStore password is required from the environment");
            }
            if (keyStore != null && keyStorePassword.length == 0) {
                throw new IllegalArgumentException(
                        "TLS keyStore password is required from the environment");
            }
        }
    }

    public static TlsClientConfig disabled() {
        return new TlsClientConfig(false, null, null, null, null, null, null);
    }

    public static TlsClientConfig fromSources(Map<String, String> environment,
                                              Properties properties) {
        Path trustStore = pathValue(environment, properties,
                ArbiterKeys.Env.TLS_TRUSTSTORE, ConfigKeys.Client.Tls.TRUSTSTORE);
        Path keyStore = pathValue(environment, properties,
                ArbiterKeys.Env.TLS_KEYSTORE, ConfigKeys.Client.Tls.KEYSTORE);
        boolean enabled = booleanValue(environment, properties,
                ArbiterKeys.Env.TLS_ENABLED, ConfigKeys.Client.Tls.ENABLED, false);
        if (!enabled && (trustStore != null || keyStore != null)) {
            enabled = true;
        }
        if (!enabled) {
            return disabled();
        }
        return new TlsClientConfig(
                true,
                trustStore,
                value(environment, properties,
                        ArbiterKeys.Env.TLS_TRUSTSTORE_TYPE, ConfigKeys.Client.Tls.TRUSTSTORE_TYPE, "PKCS12"),
                secret(environment, ArbiterKeys.Env.TLS_TRUSTSTORE_PASSWORD),
                keyStore,
                value(environment, properties,
                        ArbiterKeys.Env.TLS_KEYSTORE_TYPE, ConfigKeys.Client.Tls.KEYSTORE_TYPE, "PKCS12"),
                secret(environment, ArbiterKeys.Env.TLS_KEYSTORE_PASSWORD)
        );
    }

    @Override
    public char[] trustStorePassword() {
        return trustStorePassword.clone();
    }

    @Override
    public char[] keyStorePassword() {
        return keyStorePassword.clone();
    }

    private static String normalizeType(String value) {
        return value == null || value.isBlank() ? "PKCS12" : value.trim();
    }

    private static boolean booleanValue(Map<String, String> environment,
                                        Properties properties, String environmentKey,
                                        String propertyKey, boolean fallback) {
        String value = value(environment, properties, environmentKey, propertyKey, null);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static String value(Map<String, String> environment, Properties properties,
                                String environmentKey, String propertyKey, String fallback) {
        String fromEnvironment = environment.get(environmentKey);
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromEnvironment;
        }
        String fromProperties = properties.getProperty(propertyKey);
        return fromProperties == null || fromProperties.isBlank()
                ? fallback : fromProperties;
    }

    private static Path pathValue(Map<String, String> environment, Properties properties,
                                  String environmentKey, String propertyKey) {
        String value = value(environment, properties, environmentKey, propertyKey, null);
        return value == null ? null : Path.of(value).toAbsolutePath().normalize();
    }

    private static char[] secret(Map<String, String> environment, String key) {
        String value = environment.get(key);
        return value == null ? new char[0] : value.toCharArray();
    }
}
