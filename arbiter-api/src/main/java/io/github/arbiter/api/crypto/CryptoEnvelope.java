package io.github.arbiter.api.crypto;

import java.util.Optional;

/**
 * Self-describing ciphertext: {@code arbiter:v<version>:<algorithm>:<keyId>:<payload>}.
 * The payload layout belongs to the algorithm; decryption never guesses the
 * algorithm from the bytes.
 */
public record CryptoEnvelope(int version, String algorithm, String keyId, String payload) {
    public static final String PREFIX = "arbiter";
    public static final int CURRENT_VERSION = 1;

    public CryptoEnvelope {
        if (algorithm == null || algorithm.isBlank()) {
            throw new IllegalArgumentException("algorithm must not be blank");
        }
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("keyId must not be blank");
        }
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("payload must not be blank");
        }
    }

    public String encode() {
        return PREFIX + ":v" + version + ":" + algorithm + ":" + keyId + ":" + payload;
    }

    /**
     * Parses an envelope, or returns empty for text that is not an envelope.
     * Malformed envelopes throw {@link CryptoException}.
     */
    public static Optional<CryptoEnvelope> parse(String text) {
        if (text == null || !text.startsWith(PREFIX + ":")) {
        return Optional.empty();
        }
        String[] parts = text.split(":", 5);
        if (parts.length != 5 || !parts[1].matches("v\\d+")) {
            throw new CryptoException(CryptoException.Reason.MALFORMED_ENVELOPE, text);
        }
        int version;
        try {
            version = Integer.parseInt(parts[1].substring(1));
        } catch (NumberFormatException invalid) {
            throw new CryptoException(CryptoException.Reason.MALFORMED_ENVELOPE, text);
        }
        try {
        return Optional.of(
                    new CryptoEnvelope(version, parts[2], parts[3], parts[4]));
        } catch (IllegalArgumentException invalid) {
            throw new CryptoException(CryptoException.Reason.MALFORMED_ENVELOPE, text);
        }
    }
}
