package io.github.arbiter.server.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * Purpose-specific subkeys derived from the crypto.key master key. This keeps
 * one key file without reusing identical raw keys for encryption and signing.
 */
public final class ServerKeyDerivation {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final byte[] SALT =
            "arbiter-server-key-derivation-v1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] USER_STORE_INFO =
            "arbiter-user-store-v1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] TOKEN_SIGNING_INFO =
            "arbiter-token-signing-v1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] AGENT_STATE_INFO =
            "arbiter-agent-state-v1".getBytes(StandardCharsets.US_ASCII);

    private ServerKeyDerivation() {
    }

    public static byte[] userStore(byte[] masterKey) {
        return derive(masterKey, USER_STORE_INFO);
    }

    public static byte[] tokenSigning(byte[] masterKey) {
        return derive(masterKey, TOKEN_SIGNING_INFO);
    }

    public static byte[] agentState(byte[] masterKey) {
        return derive(masterKey, AGENT_STATE_INFO);
    }

    private static byte[] derive(byte[] masterKey, byte[] info) {
        if (masterKey == null || masterKey.length == 0) {
            throw new IllegalArgumentException("master key must not be empty");
        }
        try {
            Mac extract = Mac.getInstance(HMAC_ALGORITHM);
            extract.init(new SecretKeySpec(SALT, HMAC_ALGORITHM));
            byte[] pseudoRandomKey = extract.doFinal(masterKey);

            Mac expand = Mac.getInstance(HMAC_ALGORITHM);
            expand.init(new SecretKeySpec(pseudoRandomKey, HMAC_ALGORITHM));
            expand.update(info);
            expand.update((byte) 1);
            byte[] result = expand.doFinal();
            Arrays.fill(pseudoRandomKey, (byte) 0);
            return result;
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("HKDF-SHA256 is not available", failure);
        }
    }
}
