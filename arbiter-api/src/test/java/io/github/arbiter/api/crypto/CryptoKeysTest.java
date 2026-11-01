package io.github.arbiter.api.crypto;

import io.github.arbiter.api.config.ArbiterKeys;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CryptoKeysTest {
    @TempDir
    Path home;

    private String previousHome;

    @BeforeEach
    void useTemporaryHome() {
        previousHome = System.getProperty(ArbiterKeys.SystemProperty.HOME);
        System.setProperty(ArbiterKeys.SystemProperty.HOME, home.toString());
    }

    @AfterEach
    void restoreHome() {
        if (previousHome == null) {
            System.clearProperty(ArbiterKeys.SystemProperty.HOME);
        } else {
            System.setProperty(ArbiterKeys.SystemProperty.HOME, previousHome);
        }
    }

    @Test
    void encryptsWithAnEnvelopeAndRoundTrips() throws Exception {
        assertTrue(CryptoKeys.ensure());

        String ciphertext = CryptoKeys.encrypt("hello 中文");

        assertTrue(ciphertext.startsWith("arbiter:v1:aes-256-gcm:k1:"), ciphertext);
        assertEquals("hello 中文", CryptoKeys.decrypt(ciphertext));
    }

    @Test
    void rejectsBareBase64Payloads() throws Exception {
        assertTrue(CryptoKeys.ensure());
        String bare = AesTextCipher.encrypt("bare", CryptoKeys.active().key());

        CryptoException failure = assertThrows(CryptoException.class,
                () -> CryptoKeys.decrypt(bare));
        assertEquals(CryptoException.Reason.MALFORMED_ENVELOPE, failure.reason());
    }

    @Test
    void rotationKeepsOldCiphertextDecryptable() throws Exception {
        assertTrue(CryptoKeys.ensure());
        KeyMaterial first = CryptoKeys.active();
        String oldCiphertext = CryptoKeys.encrypt("old");

        byte[] second = new byte[CryptoKeys.KEY_BYTES];
        for (int index = 0; index < second.length; index++) {
            second[index] = (byte) (0x80 + index);
        }
        String content = "version=1\nactive=k2\n"
                + "key.k1=" + Base64.getEncoder().encodeToString(first.key()) + "\n"
                + "key.k2=" + Base64.getEncoder().encodeToString(second) + "\n";
        Files.writeString(CryptoKeys.path(), content, StandardCharsets.UTF_8);

        String newCiphertext = CryptoKeys.encrypt("new");

        assertTrue(newCiphertext.contains(":k2:"), newCiphertext);
        assertEquals("new", CryptoKeys.decrypt(newCiphertext));
        assertEquals("old", CryptoKeys.decrypt(oldCiphertext));
    }

    @Test
    void rejectsUnknownAlgorithms() throws Exception {
        assertTrue(CryptoKeys.ensure());
        String payload = CryptoEnvelope.parse(CryptoKeys.encrypt("x"))
                .orElseThrow().payload();

        CryptoException failure = assertThrows(CryptoException.class,
                () -> CryptoKeys.decrypt("arbiter:v1:rot13:k1:" + payload));
        assertEquals(CryptoException.Reason.UNSUPPORTED_ALGORITHM, failure.reason());
    }

    @Test
    void rejectsUnknownKeyIds() throws Exception {
        assertTrue(CryptoKeys.ensure());
        String payload = CryptoEnvelope.parse(CryptoKeys.encrypt("x"))
                .orElseThrow().payload();

        CryptoException failure = assertThrows(CryptoException.class,
                () -> CryptoKeys.decrypt("arbiter:v1:aes-256-gcm:k9:" + payload));
        assertEquals(CryptoException.Reason.UNKNOWN_KEY_ID, failure.reason());
    }

    @Test
    void rejectsUnsupportedEnvelopeVersions() throws Exception {
        assertTrue(CryptoKeys.ensure());
        String payload = CryptoEnvelope.parse(CryptoKeys.encrypt("x"))
                .orElseThrow().payload();

        CryptoException failure = assertThrows(CryptoException.class,
                () -> CryptoKeys.decrypt("arbiter:v2:aes-256-gcm:k1:" + payload));
        assertEquals(CryptoException.Reason.UNSUPPORTED_VERSION, failure.reason());
    }

    @Test
    void rejectsMalformedEnvelopes() throws Exception {
        assertTrue(CryptoKeys.ensure());

        CryptoException failure = assertThrows(CryptoException.class,
                () -> CryptoKeys.decrypt("arbiter:v1:aes-256-gcm:k1"));
        assertEquals(CryptoException.Reason.MALFORMED_ENVELOPE, failure.reason());
    }
}
