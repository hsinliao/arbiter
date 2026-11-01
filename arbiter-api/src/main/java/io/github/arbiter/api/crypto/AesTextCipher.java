package io.github.arbiter.api.crypto;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM text encryption with a caller-supplied 256-bit key.
 *
 * <p>The output is {@code Base64(iv || ciphertext || tag)} where the IV is
 * 12 random bytes and the tag is 128 bits. The key is accepted as 64 hex
 * characters or Base64 encoding of 32 bytes.</p>
 */
public final class AesTextCipher {
    public static final int KEY_BYTES = 32;

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private AesTextCipher() {
    }

    public static String encrypt(String plaintext, byte[] key) {
        requireKey(key);
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(
                    (plaintext == null ? "" : plaintext).getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("AES-GCM encryption failed", failure);
        }
    }

    public static String decrypt(String encoded, byte[] key) {
        requireKey(key);
        byte[] combined;
        try {
            combined = Base64.getDecoder().decode(
                    encoded == null ? "" : encoded.trim());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("ciphertext is not valid Base64", invalid);
        }
        if (combined.length < IV_BYTES + TAG_BITS / 8) {
            throw new IllegalArgumentException("ciphertext is too short");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS,
                            Arrays.copyOfRange(combined, 0, IV_BYTES)));
            return new String(cipher.doFinal(combined, IV_BYTES,
                    combined.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (AEADBadTagException wrongKeyOrTampered) {
            throw new IllegalArgumentException(
                    "ciphertext authentication failed", wrongKeyOrTampered);
        } catch (GeneralSecurityException failure) {
            throw new IllegalArgumentException("AES-GCM decryption failed", failure);
        }
    }

    private static void requireKey(byte[] key) {
        if (key == null || key.length != KEY_BYTES) {
            throw new IllegalArgumentException("AES key must be 32 bytes");
        }
    }
}
