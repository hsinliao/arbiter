package io.github.arbiter.command.module.crypto;

import io.github.arbiter.api.crypto.CryptoException;
import io.github.arbiter.api.crypto.CryptoKeys;
import io.github.arbiter.core.exception.DomainException;
import io.github.arbiter.core.exception.UsageException;

/** Maps crypto-layer reasons to the CLI error contract. */
final class CryptoErrors {
    private CryptoErrors() {
    }

    static DomainException map(CryptoException failure) {
        return switch (failure.reason()) {
            case HOME_MISSING ->
                    new UsageException("err.crypto.homeMissing");
            case KEY_NOT_INITIALIZED ->
                    new UsageException("err.crypto.keyNotInitialized");
            case KEY_RING_INVALID ->
                    new UsageException("err.crypto.keyInvalid");
            case UNSUPPORTED_ALGORITHM ->
                    new UsageException("err.crypto.unsupportedAlgorithm",
                            failure.detail());
            case UNKNOWN_KEY_ID ->
                    new UsageException("err.crypto.unknownKey", failure.detail());
            case UNSUPPORTED_VERSION, MALFORMED_ENVELOPE ->
                    new UsageException("err.crypto.malformedEnvelope");
            case DECRYPT_FAILED ->
                    new DomainException(1, "err.crypto.decryptFailed");
        };
    }

    static UsageException unreadable() {
        return new UsageException("err.crypto.keyUnreadable", CryptoKeys.path());
    }
}
