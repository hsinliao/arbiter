package io.github.arbiter.client.tls;

import io.github.arbiter.client.config.TlsClientConfig;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;

public final class ClientSslContextFactory {
    private ClientSslContextFactory() {
    }

    public static SSLContext create(TlsClientConfig settings)
            throws GeneralSecurityException, IOException {
        KeyManagerFactory keyManagers = null;
        if (settings.keyStore() != null) {
            KeyStore keyStore = load(settings.keyStore(), settings.keyStoreType(),
                    settings.keyStorePassword());
            keyManagers = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            keyManagers.init(keyStore, settings.keyStorePassword());
        }
        TrustManagerFactory trustManagers = null;
        if (settings.trustStore() != null) {
            KeyStore trustStore = load(settings.trustStore(), settings.trustStoreType(),
                    settings.trustStorePassword());
            trustManagers = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            trustManagers.init(trustStore);
        }
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers == null ? null : keyManagers.getKeyManagers(),
                trustManagers == null ? null : trustManagers.getTrustManagers(),
                new SecureRandom());
        return context;
    }

    private static KeyStore load(java.nio.file.Path path, String type, char[] password)
            throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance(type);
        try (InputStream input = java.nio.file.Files.newInputStream(path)) {
            store.load(input, password);
        }
        return store;
    }
}
