package io.github.arbiter.api.crypto;

import io.github.arbiter.api.config.ArbiterHome;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * The shared crypto key ring.
 *
 * <p>The server creates {@code <arbiter.home>/conf/security/crypto.key} on its
 * first start. The file is a small properties document:</p>
 *
 * <pre>
 * version=1
 * active=k1
 * key.k1=&lt;Base64 32 bytes&gt;
 * key.k2=&lt;Base64 32 bytes&gt;
 * </pre>
 *
 * <p>Encryption uses the active key; decryption resolves the envelope's key id
 * against the ring, so old ciphertext keeps decrypting after a rotation. The
 * file is created with owner-only permissions where the filesystem supports
 * POSIX modes.</p>
 */
public final class CryptoKeys {
    public static final String FILE_NAME = "crypto.key";
    public static final int KEY_BYTES = 32;
    public static final int RING_VERSION = 1;
    public static final String DEFAULT_KEY_ID = "k1";

    private static final String KEY_PREFIX = "key.";
    private static final SecureRandom RANDOM = new SecureRandom();

    private CryptoKeys() {
    }

    /** Returns the fixed key path, or {@code null} when {@code arbiter.home} is unset. */
    public static Path path() {
        Path securityDirectory = ArbiterHome.securityDirectory();
        return securityDirectory == null ? null : securityDirectory.resolve(FILE_NAME);
    }

    /**
     * Reads the ring, or creates it with one default key when it is missing.
     *
     * @return {@code true} when this call created the file
     */
    public static boolean ensure() throws IOException {
        Path path = requirePath();
        if (Files.isRegularFile(path)) {
            readRing(path);
            return false;
        }
        Files.createDirectories(path.getParent());
        byte[] key = new byte[KEY_BYTES];
        RANDOM.nextBytes(key);
        String content = "version=" + RING_VERSION + "\n"
                + "active=" + DEFAULT_KEY_ID + "\n"
                + KEY_PREFIX + DEFAULT_KEY_ID + "="
                + Base64.getEncoder().encodeToString(key) + "\n";
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException raced) {
            // Another process created it first; validate that copy instead.
            readRing(path);
            return false;
        }
        restrictPermissions(path);
        return true;
    }

    /** Reads the ring; fails when the file has not been generated. */
    public static KeyRing ring() throws IOException {
        Path path = requirePath();
        if (!Files.isRegularFile(path)) {
            throw new CryptoException(CryptoException.Reason.KEY_NOT_INITIALIZED,
                    path.toString());
        }
        return readRing(path);
    }

    public static KeyMaterial active() throws IOException {
        return ring().active();
    }

    public static KeyMaterial key(String id) throws IOException {
        return ring().find(id).orElseThrow(
                () -> new CryptoException(CryptoException.Reason.UNKNOWN_KEY_ID, id));
    }

    /** Encrypts with the active key and the default suite. */
    public static String encrypt(String plaintext) throws IOException {
        KeyMaterial active = active();
        CipherSuite suite = CipherSuites.defaultSuite();
        return new CryptoEnvelope(CryptoEnvelope.CURRENT_VERSION, suite.id(),
                active.id(), suite.encrypt(plaintext, active.key())).encode();
    }

    /**
     * Decrypts an envelope. The algorithm and key id come from the envelope;
     * bare Base64 payloads are rejected.
     */
    public static String decrypt(String ciphertext) throws IOException {
        CryptoEnvelope envelope = CryptoEnvelope.parse(ciphertext)
                .orElseThrow(() -> new CryptoException(
                        CryptoException.Reason.MALFORMED_ENVELOPE, ciphertext));
        if (envelope.version() != CryptoEnvelope.CURRENT_VERSION) {
            throw new CryptoException(CryptoException.Reason.UNSUPPORTED_VERSION,
                    "v" + envelope.version());
        }
        KeyRing ring = ring();
        KeyMaterial key = ring.find(envelope.keyId()).orElseThrow(
                () -> new CryptoException(CryptoException.Reason.UNKNOWN_KEY_ID,
                        envelope.keyId()));
        return CipherSuites.byId(envelope.algorithm())
                .decrypt(envelope.payload(), key.key());
    }

    private static KeyRing readRing(Path path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        String version = properties.getProperty("version");
        if (!String.valueOf(RING_VERSION).equals(version)) {
            throw new CryptoException(CryptoException.Reason.KEY_RING_INVALID,
                    "version must be " + RING_VERSION);
        }
        String active = properties.getProperty("active");
        if (active == null || active.isBlank()) {
            throw new CryptoException(CryptoException.Reason.KEY_RING_INVALID,
                    "active key id is missing");
        }
        Map<String, byte[]> keys = new LinkedHashMap<>();
        for (String name : properties.stringPropertyNames()) {
            if (!name.startsWith(KEY_PREFIX)) {
                continue;
            }
            String id = name.substring(KEY_PREFIX.length());
            if (id.isBlank()) {
                throw new CryptoException(CryptoException.Reason.KEY_RING_INVALID,
                        "empty key id");
            }
            keys.put(id, decodeKey(id, properties.getProperty(name)));
        }
        if (!keys.containsKey(active)) {
            throw new CryptoException(CryptoException.Reason.KEY_RING_INVALID,
                    "active key is not in the ring: " + active);
        }
        return new KeyRing(active, keys);
    }

    private static byte[] decodeKey(String id, String encoded) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(
                    encoded == null ? "" : encoded.trim());
        } catch (IllegalArgumentException invalid) {
            throw new CryptoException(CryptoException.Reason.KEY_RING_INVALID,
                    "key." + id + " is not valid Base64");
        }
        if (key.length != KEY_BYTES) {
            throw new CryptoException(CryptoException.Reason.KEY_RING_INVALID,
                    "key." + id + " must be " + KEY_BYTES + " bytes");
        }
        return key;
    }

    private static Path requirePath() {
        Path path = path();
        if (path == null) {
            throw new CryptoException(CryptoException.Reason.HOME_MISSING,
                    "arbiter.home is not set; start the server through bin/server-start");
        }
        return path;
    }

    private static void restrictPermissions(Path path) {
        try {
            Files.setPosixFilePermissions(path,
                    PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows and non-POSIX filesystems rely on the user profile ACL.
        }
    }
}
