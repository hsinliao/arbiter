package io.github.arbiter.server.auth;

import io.github.arbiter.api.crypto.KeyRing;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenServiceTest {
    @Test
    void issuedTokenVerifiesAndExpires() {
        String secret = "0123456789abcdef0123456789abcdef";
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        TokenService issuer = new TokenService(secret, Duration.ofHours(1),
                Clock.fixed(now, ZoneOffset.UTC));
        UserAccount user = UserAccount.create("alice", "password", "reader", true);

        TokenService.IssuedToken issued = issuer.issue(user);

        assertTrue(issuer.verify(issued.token()).isPresent());
        assertEquals("alice", issuer.verify(issued.token()).orElseThrow().subject());

        TokenService later = new TokenService(secret, Duration.ofHours(1),
                Clock.fixed(now.plus(Duration.ofHours(2)), ZoneOffset.UTC));
        assertTrue(later.verify(issued.token()).isEmpty());
    }

    @Test
    void tamperedTokenIsRejected() {
        TokenService service = new TokenService(
                "0123456789abcdef0123456789abcdef", Duration.ofHours(1), Clock.systemUTC());
        String token = service.issue(
                UserAccount.create("alice", "password", "reader", true)).token();

        int separator = token.indexOf('.');
        char[] payload = token.substring(0, separator).toCharArray();
        payload[0] = payload[0] == 'A' ? 'B' : 'A';
        String tampered = new String(payload) + token.substring(separator);
        assertTrue(service.verify(tampered).isEmpty());
    }

    @Test
    void tokenRemainsValidAfterActiveKeyRotation() {
        byte[] oldKey = new byte[32];
        byte[] newKey = new byte[32];
        Arrays.fill(oldKey, (byte) 1);
        Arrays.fill(newKey, (byte) 2);
        UserAccount user = UserAccount.create("alice", "password", "reader", true);
        String token = new TokenService(new KeyRing("k1", Map.of("k1", oldKey)),
                Duration.ofHours(1), Clock.systemUTC()).issue(user).token();
        KeyRing rotated = new KeyRing("k2",
                Map.of("k1", oldKey, "k2", newKey));

        assertTrue(new TokenService(rotated, Duration.ofHours(1), Clock.systemUTC())
                .verify(token).isPresent());
    }
}
