package io.github.arbiter.server.tls;

import org.springframework.boot.ssl.DefaultSslBundleRegistry;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundleKey;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.ssl.SslOptions;
import org.springframework.boot.ssl.SslStoreBundle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

/** Builds a Boot {@link SslBundle} from the fixed server TLS material. */
public final class SslBundleFactory {
    public static final String BUNDLE_NAME = "arbiter";

    private SslBundleFactory() {
    }

    public static SslBundles create(TlsSettings settings)
            throws GeneralSecurityException, IOException {
        KeyStore keyStore = load(settings.keyStore(), settings.keyStoreType(),
                settings.keyStorePassword());
        KeyStore trustStore = settings.clientAuth()
                ? load(settings.trustStore(), settings.trustStoreType(),
                        settings.trustStorePassword())
                : null;
        String keyPassword = new String(settings.keyStorePassword());
        SslStoreBundle stores = SslStoreBundle.of(keyStore, keyPassword, trustStore);
        SslBundleKey key = SslBundleKey.of(keyPassword);
        SslOptions options = SslOptions.of(null,
                settings.protocols().toArray(String[]::new));
        SslBundle bundle = SslBundle.of(stores, key, options);
        return new DefaultSslBundleRegistry(BUNDLE_NAME, bundle);
    }

    private static KeyStore load(java.nio.file.Path path, String type, char[] password)
            throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance(type);
        try (InputStream input = Files.newInputStream(path)) {
            store.load(input, password);
        }
        return store;
    }
}
