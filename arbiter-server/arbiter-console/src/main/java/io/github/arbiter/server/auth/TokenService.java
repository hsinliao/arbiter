package io.github.arbiter.server.auth;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import io.github.arbiter.api.crypto.KeyMaterial;
import io.github.arbiter.api.crypto.KeyRing;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class TokenService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final ObjectMapper mapper;
    private final KeyRing keys;
    private final Duration ttl;
    private final Clock clock;

    public TokenService(String configuredSecret, Duration ttl, Clock clock) {
        this(configuredSecret, ttl, clock, new ObjectMapper());
    }

    TokenService(String configuredSecret, Duration ttl, Clock clock, ObjectMapper mapper) {
        this(secretRing(configuredSecret), ttl, clock, mapper);
    }

    public TokenService(KeyRing keys, Duration ttl, Clock clock) {
        this(keys, ttl, clock, new ObjectMapper());
    }

    TokenService(KeyRing keys, Duration ttl, Clock clock, ObjectMapper mapper) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Token TTL must be positive");
        }
        this.mapper = mapper;
        this.ttl = ttl;
        this.clock = clock;
        if (keys == null) {
            throw new IllegalArgumentException("Token key ring must not be null");
        }
        this.keys = keys;
    }

    public IssuedToken issue(UserAccount account) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(ttl);
        ObjectNode claims = mapper.createObjectNode();
        claims.put("sub", account.username());
        claims.put("scope", account.scope());
        claims.put("iat", issuedAt.getEpochSecond());
        claims.put("exp", expiresAt.getEpochSecond());
        claims.put("jti", UUID.randomUUID().toString());
        KeyMaterial active = keys.active();
        claims.put("kid", active.id());
        try {
            String payload = encode(mapper.writeValueAsBytes(claims));
            String signature = encode(sign(payload,
                    ServerKeyDerivation.tokenSigning(active.key())));
            return new IssuedToken(payload + "." + signature, expiresAt);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to issue authentication token");
        }
    }

    public Optional<TokenClaims> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        int separator = token.indexOf('.');
        if (separator <= 0 || separator != token.lastIndexOf('.')
                || separator == token.length() - 1) {
            return Optional.empty();
        }
        String payload = token.substring(0, separator);
        String encodedSignature = token.substring(separator + 1);
        try {
            JsonNode claims = mapper.readTree(Base64.getUrlDecoder().decode(payload));
            String keyId = text(claims, "kid");
            KeyMaterial signingKey = keyId == null ? null
                    : keys.find(keyId).orElse(null);
            if (signingKey == null) {
                return Optional.empty();
            }
            byte[] suppliedSignature = Base64.getUrlDecoder().decode(encodedSignature);
            byte[] expectedSignature = sign(payload,
                    ServerKeyDerivation.tokenSigning(signingKey.key()));
            if (!MessageDigest.isEqual(expectedSignature, suppliedSignature)) {
                return Optional.empty();
            }
            String subject = text(claims, "sub");
            String scope = text(claims, "scope");
            long issued = claims.path("iat").asLong(-1);
            long expires = claims.path("exp").asLong(-1);
            if (subject == null || scope == null || issued < 0 || expires < 0) {
                return Optional.empty();
            }
            Instant expiresAt = Instant.ofEpochSecond(expires);
            if (!expiresAt.isAfter(clock.instant())) {
                return Optional.empty();
            }
            return Optional.of(new TokenClaims(subject, scope,
                    Instant.ofEpochSecond(issued), expiresAt));
        } catch (Exception invalidToken) {
            return Optional.empty();
        }
    }

    private byte[] sign(String payload, byte[] signingKey)
            throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
        return mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
    }

    private static KeyRing secretRing(String configuredSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            throw new IllegalArgumentException("Token secret must not be blank");
        }
        return new KeyRing("test", Map.of("test",
                configuredSecret.getBytes(StandardCharsets.UTF_8)));
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank()
                ? value.asText() : null;
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }

    public record TokenClaims(String subject, String scope, Instant issuedAt,
                              Instant expiresAt) {
    }
}
