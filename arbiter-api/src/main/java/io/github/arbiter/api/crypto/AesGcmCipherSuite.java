package io.github.arbiter.api.crypto;

/**
 * Default suite: AES-256-GCM with a 12-byte random IV and a 128-bit tag. The
 * payload is {@code Base64(iv || ciphertext || tag)}.
 */
public final class AesGcmCipherSuite implements CipherSuite {
    public static final String ID = "aes-256-gcm";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String encrypt(String plaintext, byte[] key) {
        return AesTextCipher.encrypt(plaintext, key);
    }

    @Override
    public String decrypt(String payload, byte[] key) {
        try {
            return AesTextCipher.decrypt(payload, key);
        } catch (IllegalArgumentException failure) {
            throw new CryptoException(CryptoException.Reason.DECRYPT_FAILED,
                    failure.getMessage());
        }
    }
}
