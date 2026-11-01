package io.github.arbiter.api.crypto;

/**
 * Crypto failure with a stable reason so callers can map it to their own error
 * contract without parsing messages.
 */
public final class CryptoException extends RuntimeException {
    public enum Reason {
        DECRYPT_FAILED,
        UNSUPPORTED_ALGORITHM,
        UNKNOWN_KEY_ID,
        UNSUPPORTED_VERSION,
        MALFORMED_ENVELOPE,
        HOME_MISSING,
        KEY_NOT_INITIALIZED,
        KEY_RING_INVALID
    }

    private final Reason reason;
    private final String detail;

    public CryptoException(Reason reason, String detail) {
        super(reason + (detail == null || detail.isBlank() ? "" : ": " + detail));
        this.reason = reason;
        this.detail = detail == null ? "" : detail;
    }

    public Reason reason() {
        return reason;
    }

    public String detail() {
        return detail;
    }
}
