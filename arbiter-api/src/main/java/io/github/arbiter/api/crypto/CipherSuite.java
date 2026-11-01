package io.github.arbiter.api.crypto;

/**
 * One cipher implementation. {@code encrypt} returns the suite-specific
 * payload; {@code decrypt} accepts the payload stored in a
 * {@link CryptoEnvelope}.
 */
public interface CipherSuite {
    String id();

    String encrypt(String plaintext, byte[] key);

    String decrypt(String payload, byte[] key);
}
