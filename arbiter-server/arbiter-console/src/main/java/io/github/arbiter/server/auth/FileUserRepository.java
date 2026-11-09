package io.github.arbiter.server.auth;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import io.github.arbiter.api.config.ArbiterHome;
import io.github.arbiter.api.crypto.CipherSuite;
import io.github.arbiter.api.crypto.CipherSuites;
import io.github.arbiter.api.crypto.CryptoEnvelope;
import io.github.arbiter.api.crypto.CryptoKeys;
import io.github.arbiter.api.crypto.KeyMaterial;
import io.github.arbiter.api.crypto.KeyRing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Encrypted persistent user repository. Its single {@code users.dat} file is
 * the sole source of truth; no user records are cached in memory.
 */
public final class FileUserRepository implements MutableUserRepository {
    public static final String FILE_NAME = "users.dat";
    public static final String INITIAL_ADMIN_USERNAME = "admin";
    private static final int FILE_VERSION = 1;
    private static final int INITIAL_PASSWORD_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path file;
    private final KeyRing keys;
    private final ObjectMapper mapper;

    private FileUserRepository(Path file, KeyRing keys, ObjectMapper mapper) {
        this.file = file;
        this.keys = keys;
        this.mapper = mapper;
    }

    public static OpenResult openOrCreate() throws IOException {
        Path securityDirectory = ArbiterHome.securityDirectory();
        if (securityDirectory == null) {
            throw new IllegalStateException(
                    "arbiter.home is not set; start the server through bin/server-start");
        }
        CryptoKeys.ensure();
        return openOrCreate(securityDirectory.resolve(FILE_NAME), CryptoKeys.ring());
    }

    static OpenResult openOrCreate(Path file, KeyRing keys) throws IOException {
        ObjectMapper mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        Path absoluteFile = file.toAbsolutePath().normalize();
        FileUserRepository repository = new FileUserRepository(
                absoluteFile, keys, mapper);
        if (Files.isRegularFile(absoluteFile)) {
            repository.readFile();
            return new OpenResult(repository, Optional.empty());
        }

        Files.createDirectories(absoluteFile.getParent());
        String password = randomPassword();
        UserAccount admin = UserAccount.create(
                INITIAL_ADMIN_USERNAME, password, "admin", true);
        try {
            repository.writeFile(new UserFile(FILE_VERSION, Instant.now().toString(),
                    List.of(StoredUser.from(admin))), false);
        } catch (FileAlreadyExistsException raced) {
            repository.readFile();
            return new OpenResult(repository, Optional.empty());
        }
        return new OpenResult(repository,
                Optional.of(new InitialAdmin(admin.username(), password)));
    }

    public Path path() {
        return file;
    }

    @Override
    public synchronized Optional<UserAccount> findByUsername(String username) {
        return findAllInternal().stream()
                .filter(account -> account.username().equals(username))
                .findFirst();
    }

    @Override
    public synchronized boolean hasUsers() {
        return !findAllInternal().isEmpty();
    }

    @Override
    public synchronized List<UserAccount> findAll() {
        return findAllInternal();
    }

    @Override
    public synchronized void add(UserAccount account) throws IOException {
        UserFile current = readFile();
        List<StoredUser> users = new ArrayList<>(current.users());
        if (users.stream().anyMatch(user ->
                user.username().equals(account.username()))) {
            throw new IllegalArgumentException("Duplicate server user: "
                    + account.username());
        }
        users.add(StoredUser.from(account));
        writeUsers(users, current.createdAt());
    }

    @Override
    public synchronized void update(UserAccount account) throws IOException {
        UserFile current = readFile();
        List<StoredUser> users = new ArrayList<>(current.users());
        for (int index = 0; index < users.size(); index++) {
            if (users.get(index).username().equals(account.username())) {
                users.set(index, StoredUser.from(account));
                writeUsers(users, current.createdAt());
                return;
            }
        }
        throw new IllegalArgumentException("Unknown server user: "
                + account.username());
    }

    @Override
    public synchronized boolean delete(String username) throws IOException {
        UserFile current = readFile();
        List<StoredUser> users = new ArrayList<>(current.users());
        boolean removed = users.removeIf(user -> user.username().equals(username));
        if (removed) {
            writeUsers(users, current.createdAt());
        }
        return removed;
    }

    private List<UserAccount> findAllInternal() {
        try {
            return readFile().users().stream()
                    .map(StoredUser::toAccount)
                    .toList();
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "cannot read user store: " + file, failure);
        }
    }

    private UserFile readFile() throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("user store does not exist: " + file);
        }
        String encoded = Files.readString(file, StandardCharsets.UTF_8).trim();
        CryptoEnvelope envelope = CryptoEnvelope.parse(encoded)
                .orElseThrow(() -> new IllegalArgumentException(
                        "user store is not an encrypted envelope: " + file));
        if (envelope.version() != CryptoEnvelope.CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported user store version: " + envelope.version());
        }
        KeyMaterial master = keys.find(envelope.keyId()).orElseThrow(
                () -> new IllegalArgumentException(
                        "user store key id is unavailable: " + envelope.keyId()));
        CipherSuite suite = CipherSuites.byId(envelope.algorithm());
        String plaintext = suite.decrypt(envelope.payload(),
                ServerKeyDerivation.userStore(master.key()));
        UserFile userFile;
        try {
            userFile = mapper.readValue(plaintext, UserFile.class);
        } catch (JacksonException failure) {
            throw new IOException("user store is not valid JSON: " + file, failure);
        }
        validate(userFile);
        return userFile;
    }

    private void validate(UserFile userFile) {
        if (userFile.version() != FILE_VERSION) {
            throw new IllegalArgumentException("unsupported user store version: "
                    + userFile.version());
        }
        if (userFile.users() == null) {
            throw new IllegalArgumentException("user store has no users list");
        }
        Set<String> usernames = new LinkedHashSet<>();
        for (StoredUser user : userFile.users()) {
            if (!usernames.add(user.username())) {
                throw new IllegalArgumentException(
                        "duplicate server user in store: " + user.username());
            }
        }
    }

    private void writeUsers(List<StoredUser> users, String createdAt) throws IOException {
        List<StoredUser> sorted = users.stream()
                .sorted(Comparator.comparing(StoredUser::username))
                .toList();
        writeFile(new UserFile(FILE_VERSION, createdAt, sorted), true);
    }

    private void writeFile(UserFile userFile, boolean replace) throws IOException {
        Path parent = file.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "users-", ".tmp");
        try {
            String plaintext;
            try {
                plaintext = mapper.writeValueAsString(userFile);
            } catch (JacksonException failure) {
                throw new IOException("cannot serialize user store", failure);
            }
            KeyMaterial active = keys.active();
            CipherSuite suite = CipherSuites.defaultSuite();
            String encoded = new CryptoEnvelope(CryptoEnvelope.CURRENT_VERSION,
                    suite.id(), active.id(),
                    suite.encrypt(plaintext,
                            ServerKeyDerivation.userStore(active.key()))).encode();
            Files.writeString(temporary, encoded + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING);
            restrictPermissions(temporary);
            try {
                if (replace) {
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
                }
            } catch (AtomicMoveNotSupportedException unsupported) {
                if (replace) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.move(temporary, file);
                }
            }
            restrictPermissions(file);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String randomPassword() {
        byte[] bytes = new byte[INITIAL_PASSWORD_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void restrictPermissions(Path path) {
        try {
            Files.setPosixFilePermissions(path,
                    PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // ACL fallback below handles Windows.
        }
        try {
            AclFileAttributeView view = Files.getFileAttributeView(
                    path, AclFileAttributeView.class);
            if (view != null) {
                UserPrincipal owner = view.getOwner();
                AclEntry ownerOnly = AclEntry.newBuilder()
                        .setType(AclEntryType.ALLOW)
                        .setPrincipal(owner)
                        .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                        .build();
                view.setAcl(List.of(ownerOnly));
            }
        } catch (IOException | RuntimeException ignored) {
            // Keep the generated store even when ACL enforcement is unavailable.
        }
        try {
            Files.setAttribute(path, "dos:hidden", true, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Dot-prefixed paths already have this convention on Unix-like systems.
        }
    }

    public record OpenResult(FileUserRepository repository,
                             Optional<InitialAdmin> initialAdmin) {
    }

    public record InitialAdmin(String username, String password) {
    }

    record UserFile(int version, String createdAt, List<StoredUser> users) {
    }

    record StoredUser(String username, String passwordHash, String scope,
                      boolean enabled) {
        static StoredUser from(UserAccount account) {
            return new StoredUser(account.username(), account.passwordHash(),
                    account.scope(), account.enabled());
        }

        UserAccount toAccount() {
            return new UserAccount(username, passwordHash, scope, enabled);
        }
    }
}
