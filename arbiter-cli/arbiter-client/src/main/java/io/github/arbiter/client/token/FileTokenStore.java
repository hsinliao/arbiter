package io.github.arbiter.client.token;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import io.github.arbiter.core.exception.LocalStorageException;
import io.github.arbiter.core.service.TokenSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * v1 credential backend. A platform credential-manager adapter can replace
 * this implementation later; the token store interface keeps that change local.
 */
public final class FileTokenStore implements TokenStore {
    private final Path credentialsFile;
    private final Supplier<String> environmentToken;
    private final ObjectMapper mapper;

    public FileTokenStore(Path credentialsFile, Supplier<String> environmentToken) {
        this.credentialsFile = credentialsFile;
        this.environmentToken = environmentToken;
        this.mapper = JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
    }

    @Override
    public Optional<ResolvedToken> resolve() {
        String fromEnvironment = environmentToken.get();
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return Optional.of(new ResolvedToken(TokenSource.ENV,
                    new StoredToken(fromEnvironment, null, null, null)));
        }
        if (!Files.isRegularFile(credentialsFile)) {
            return Optional.empty();
        }
        try {
            StoredToken stored = mapper.readValue(credentialsFile.toFile(), StoredToken.class);
            if (stored.token() == null || stored.token().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new ResolvedToken(TokenSource.FILE, stored));
        } catch (JacksonException exception) {
            throw new LocalStorageException(
                    "Unable to read local credentials: " + credentialsFile, exception);
        }
    }

    @Override
    public boolean exists() {
        return Files.isRegularFile(credentialsFile);
    }

    @Override
    public void save(StoredToken token) {
        if (token == null || token.token() == null || token.token().isBlank()) {
            throw new IllegalArgumentException("A server-issued token is required");
        }
        try {
            Path parent = credentialsFile.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            restrictDirectory(parent);
            Path temporary = Files.createTempFile(parent, ".credentials-", ".tmp");
            try {
                Files.writeString(temporary, mapper.writeValueAsString(token),
                        StandardCharsets.UTF_8);
                restrictFile(temporary);
                try {
                    Files.move(temporary, credentialsFile, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, credentialsFile, StandardCopyOption.REPLACE_EXISTING);
                }
                restrictFile(credentialsFile);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException | JacksonException exception) {
            throw new LocalStorageException(
                    "Unable to persist local credentials: " + credentialsFile, exception);
        }
    }

    @Override
    public boolean delete() {
        try {
            return Files.deleteIfExists(credentialsFile);
        } catch (IOException exception) {
            throw new LocalStorageException(
                    "Unable to delete local credentials: " + credentialsFile, exception);
        }
    }

    private static void restrictDirectory(Path directory) throws IOException {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(directory, permissions(700));
        }
        // TODO: use Windows Credential Manager instead of the fallback file backend.
    }

    private static void restrictFile(Path file) throws IOException {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(file, permissions(600));
        }
    }

    private static Set<PosixFilePermission> permissions(int mode) {
        Set<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);
        if ((mode & 400) != 0) permissions.add(PosixFilePermission.OWNER_READ);
        if ((mode & 200) != 0) permissions.add(PosixFilePermission.OWNER_WRITE);
        if ((mode & 100) != 0) permissions.add(PosixFilePermission.OWNER_EXECUTE);
        return permissions;
    }
}
