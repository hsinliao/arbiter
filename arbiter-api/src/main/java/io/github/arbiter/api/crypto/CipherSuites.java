package io.github.arbiter.api.crypto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Algorithm registry. Adding an algorithm means adding a {@link CipherSuite}
 * here; encrypt uses {@link #defaultSuite()}, decrypt selects by envelope id.
 */
public final class CipherSuites {
    private static final Map<String, CipherSuite> SUITES = suites();
    private static final CipherSuite DEFAULT = SUITES.get(AesGcmCipherSuite.ID);

    private CipherSuites() {
    }

    public static CipherSuite defaultSuite() {
        return DEFAULT;
    }

    public static CipherSuite byId(String id) {
        CipherSuite suite = id == null ? null : SUITES.get(id);
        if (suite == null) {
            throw new CryptoException(CryptoException.Reason.UNSUPPORTED_ALGORITHM,
                    id == null ? "" : id);
        }
        return suite;
    }

    private static Map<String, CipherSuite> suites() {
        Map<String, CipherSuite> suites = new LinkedHashMap<>();
        register(suites, new AesGcmCipherSuite());
        return Map.copyOf(suites);
    }

    private static void register(Map<String, CipherSuite> suites, CipherSuite suite) {
        if (suites.putIfAbsent(suite.id(), suite) != null) {
            throw new IllegalStateException("Duplicate cipher suite: " + suite.id());
        }
    }
}
