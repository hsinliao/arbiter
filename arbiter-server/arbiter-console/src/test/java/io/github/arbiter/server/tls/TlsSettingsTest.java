package io.github.arbiter.server.tls;

import io.github.arbiter.api.config.ArbiterKeys;
import io.github.arbiter.api.config.ConfigKeys;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TlsSettingsTest {
    @TempDir
    Path temporaryDirectory;

    private String previousHome;

    @BeforeEach
    void useTemporaryArbiterHome() {
        previousHome = System.getProperty(ArbiterKeys.SystemProperty.HOME);
        System.setProperty(ArbiterKeys.SystemProperty.HOME, temporaryDirectory.toString());
    }

    @AfterEach
    void restoreArbiterHome() {
        if (previousHome == null) {
            System.clearProperty(ArbiterKeys.SystemProperty.HOME);
        } else {
            System.setProperty(ArbiterKeys.SystemProperty.HOME, previousHome);
        }
    }

    @Test
    void disabledByDefault() {
        TlsSettings settings = TlsSettings.fromProperties(new Properties());
        assertFalse(settings.enabled());
    }

    @Test
    void tlsRequiresArbiterHome() {
        System.clearProperty(ArbiterKeys.SystemProperty.HOME);
        Properties properties = new Properties();
        properties.setProperty(ConfigKeys.Server.Tls.ENABLED, "true");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> TlsSettings.fromProperties(properties));
        assertTrue(failure.getMessage().contains("arbiter.home"));
    }

    @Test
    void keyStoreAndTrustStoreUseFixedSecurityDirectory() throws Exception {
        Path security = Files.createDirectories(
                temporaryDirectory.resolve("conf").resolve("security"));
        Files.writeString(security.resolve("keystore.p12"), "");
        Files.writeString(security.resolve("truststore.p12"), "");

        Properties properties = new Properties();
        properties.setProperty(ConfigKeys.Server.Tls.ENABLED, "true");
        properties.setProperty(ConfigKeys.Server.Tls.CLIENT_AUTH, "true");
        properties.setProperty(ConfigKeys.Server.Tls.KEYSTORE_PASSWORD, "changeit");
        properties.setProperty(ConfigKeys.Server.Tls.TRUSTSTORE_PASSWORD, "changeit");

        TlsSettings settings = TlsSettings.fromProperties(properties);

        assertTrue(settings.enabled());
        assertEquals(security.resolve("keystore.p12"), settings.keyStore());
        assertEquals(security.resolve("truststore.p12"), settings.trustStore());
    }

    @Test
    void keyStorePasswordMustComeFromProperties() throws Exception {
        Path security = Files.createDirectories(
                temporaryDirectory.resolve("conf").resolve("security"));
        Files.writeString(security.resolve("keystore.p12"), "");

        Properties properties = new Properties();
        properties.setProperty(ConfigKeys.Server.Tls.ENABLED, "true");

        assertThrows(IllegalArgumentException.class,
                () -> TlsSettings.fromProperties(properties));
    }

    @Test
    void clientAuthRequiresTrustStorePassword() throws Exception {
        Path security = Files.createDirectories(
                temporaryDirectory.resolve("conf").resolve("security"));
        Files.writeString(security.resolve("keystore.p12"), "");
        Files.writeString(security.resolve("truststore.p12"), "");

        Properties properties = new Properties();
        properties.setProperty(ConfigKeys.Server.Tls.ENABLED, "true");
        properties.setProperty(ConfigKeys.Server.Tls.CLIENT_AUTH, "true");
        properties.setProperty(ConfigKeys.Server.Tls.KEYSTORE_PASSWORD, "changeit");

        assertThrows(IllegalArgumentException.class,
                () -> TlsSettings.fromProperties(properties));
    }

    @Test
    void oneWayTlsHasNoTrustStore() throws Exception {
        Path security = Files.createDirectories(
                temporaryDirectory.resolve("conf").resolve("security"));
        Files.writeString(security.resolve("keystore.p12"), "");

        Properties properties = new Properties();
        properties.setProperty(ConfigKeys.Server.Tls.ENABLED, "true");
        properties.setProperty(ConfigKeys.Server.Tls.KEYSTORE_PASSWORD, "changeit");

        TlsSettings settings = TlsSettings.fromProperties(properties);

        assertTrue(settings.enabled());
        assertNull(settings.trustStore());
    }
}
