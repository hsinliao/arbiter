package io.github.arbiter.server.tls;

import io.github.arbiter.api.config.ArbiterHome;
import io.github.arbiter.api.config.ConfigKeys;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * TLS/mTLS settings come only from the server properties file. The key store and
 * trust store are fixed at {@code <arbiter.home>/conf/security/}; passwords are
 * read from the same properties file.
 */
public record TlsSettings(
        boolean enabled,
        Path keyStore,
        String keyStoreType,
        char[] keyStorePassword,
        boolean clientAuth,
        Path trustStore,
        String trustStoreType,
        char[] trustStorePassword,
        List<String> protocols
) {
    private static final List<String> DEFAULT_PROTOCOLS = List.of("TLSv1.3", "TLSv1.2");

    public TlsSettings {
        keyStoreType = normalizeType(keyStoreType);
        trustStoreType = normalizeType(trustStoreType);
        keyStorePassword = keyStorePassword == null ? new char[0] : keyStorePassword.clone();
        trustStorePassword = trustStorePassword == null ? new char[0] : trustStorePassword.clone();
        protocols = protocols == null || protocols.isEmpty()
                ? DEFAULT_PROTOCOLS : List.copyOf(protocols);
        if (enabled) {
            if (keyStore == null || !Files.isRegularFile(keyStore)) {
                throw new IllegalArgumentException(
                        "TLS keyStore does not exist: " + keyStore);
            }
            if (keyStorePassword.length == 0) {
                throw new IllegalArgumentException(
                        "tls.keystore.password is required when TLS is enabled");
            }
            if (clientAuth && (trustStore == null || !Files.isRegularFile(trustStore))) {
                throw new IllegalArgumentException(
                        "mTLS trustStore does not exist: " + trustStore);
            }
            if (clientAuth && trustStorePassword.length == 0) {
                throw new IllegalArgumentException(
                        "tls.truststore.password is required for mTLS");
            }
        }
    }

    public static TlsSettings disabled() {
        return new TlsSettings(false, null, null, null, false, null, null, null,
                DEFAULT_PROTOCOLS);
    }

    public static TlsSettings fromProperties(Properties properties) {
        boolean enabled = booleanValue(properties, ConfigKeys.Server.Tls.ENABLED, false);
        boolean clientAuth = booleanValue(properties, ConfigKeys.Server.Tls.CLIENT_AUTH, false);
        return from(enabled,
                value(properties, ConfigKeys.Server.Tls.KEYSTORE_TYPE, "PKCS12"),
                value(properties, ConfigKeys.Server.Tls.KEYSTORE_PASSWORD, null),
                clientAuth,
                value(properties, ConfigKeys.Server.Tls.TRUSTSTORE_TYPE, "PKCS12"),
                value(properties, ConfigKeys.Server.Tls.TRUSTSTORE_PASSWORD, null),
                value(properties, ConfigKeys.Server.Tls.PROTOCOLS, "TLSv1.3,TLSv1.2"));
    }

    public static TlsSettings from(boolean enabled, String keyStoreType, String keyStorePassword,
                                   boolean clientAuth, String trustStoreType,
                                   String trustStorePassword, String protocols) {
        if (!enabled) {
            return disabled();
        }
        Path securityDirectory = ArbiterHome.securityDirectory();
        if (securityDirectory == null) {
            throw new IllegalArgumentException(
                    "TLS requires arbiter.home; start the server through bin/server-start"
                    + " or bin/server-start.bat");
        }
        return new TlsSettings(
                true,
                securityDirectory.resolve("keystore.p12"),
                normalizeType(keyStoreType),
                password(keyStorePassword),
                clientAuth,
                clientAuth ? securityDirectory.resolve("truststore.p12") : null,
                normalizeType(trustStoreType),
                password(trustStorePassword),
                protocols(protocols)
        );
    }

    @Override
    public char[] keyStorePassword() {
        return keyStorePassword.clone();
    }

    @Override
    public char[] trustStorePassword() {
        return trustStorePassword.clone();
    }

    private static String normalizeType(String value) {
        return value == null || value.isBlank() ? "PKCS12" : value.trim();
    }

    private static boolean booleanValue(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value);
    }

    private static String value(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static char[] password(String value) {
        return value == null ? new char[0] : value.toCharArray();
    }

    private static List<String> protocols(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_PROTOCOLS;
        }
        List<String> protocols = new ArrayList<>();
        for (String protocol : value.split(",")) {
            if (!protocol.isBlank()) {
                protocols.add(protocol.trim());
            }
        }
        return protocols;
    }
}
