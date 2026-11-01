package io.github.arbiter.api.crypto;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CryptoEnvelopeTest {
    @Test
    void encodesAndParses() {
        CryptoEnvelope envelope = new CryptoEnvelope(1, "aes-256-gcm", "k1", "cGF5bG9hZA==");

        String encoded = envelope.encode();
        assertEquals("arbiter:v1:aes-256-gcm:k1:cGF5bG9hZA==", encoded);

        CryptoEnvelope parsed = CryptoEnvelope.parse(encoded).orElseThrow();
        assertEquals(1, parsed.version());
        assertEquals("aes-256-gcm", parsed.algorithm());
        assertEquals("k1", parsed.keyId());
        assertEquals("cGF5bG9hZA==", parsed.payload());
    }

    @Test
    void returnsEmptyForNonEnvelopeText() {
        Optional<CryptoEnvelope> parsed = CryptoEnvelope.parse("not-an-envelope");

        assertTrue(parsed.isEmpty());
    }

    @Test
    void rejectsMalformedEnvelopes() {
        for (String text : new String[]{
                "arbiter:v1:aes-256-gcm:k1",
                "arbiter:x:aes-256-gcm:k1:cGF5bG9hZA==",
                "arbiter:v1::k1:cGF5bG9hZA==",
                "arbiter:v1:aes-256-gcm::cGF5bG9hZA==",
                "arbiter:v1:aes-256-gcm:k1:"
        }) {
            assertThrows(CryptoException.class, () -> CryptoEnvelope.parse(text), text);
        }
    }
}
