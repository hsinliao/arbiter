package io.github.arbiter.server.auth;

import io.github.arbiter.api.crypto.KeyRing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileUserRepositoryTest {
    @Test
    void createsInitialAdministratorWithoutStoringPlaintextPassword(
            @TempDir Path directory) throws Exception {
        Path usersFile = directory.resolve(FileUserRepository.FILE_NAME);
        KeyRing keys = keyRing("k1", (byte) 1);

        FileUserRepository.OpenResult opened =
                FileUserRepository.openOrCreate(usersFile, keys);
        FileUserRepository.InitialAdmin initial =
                opened.initialAdmin().orElseThrow();
        String content = Files.readString(usersFile, StandardCharsets.UTF_8);

        assertEquals("admin", initial.username());
        assertTrue(initial.password().length() >= 42);
        assertTrue(opened.repository().findByUsername("admin").orElseThrow()
                .matches(initial.password()));
        assertTrue(content.startsWith("arbiter:v1:aes-256-gcm:k1:"));
        assertFalse(content.contains("passwordHash"));
        assertFalse(content.contains("username"));
        assertFalse(content.contains(initial.password()));
    }

    @Test
    void reopensExistingUsersWithoutGeneratingAnotherPassword(@TempDir Path directory)
            throws Exception {
        Path usersFile = directory.resolve(FileUserRepository.FILE_NAME);
        KeyRing keys = keyRing("k1", (byte) 1);
        FileUserRepository.InitialAdmin initial = FileUserRepository
                .openOrCreate(usersFile, keys).initialAdmin().orElseThrow();

        FileUserRepository.OpenResult reopened =
                FileUserRepository.openOrCreate(usersFile, keys);

        assertTrue(reopened.initialAdmin().isEmpty());
        assertTrue(reopened.repository().findByUsername("admin").orElseThrow()
                .matches(initial.password()));
    }

    @Test
    void persistsAddUpdateAndDelete(@TempDir Path directory) throws Exception {
        FileUserRepository repository =
                FileUserRepository.openOrCreate(
                                directory.resolve(FileUserRepository.FILE_NAME),
                                keyRing("k1", (byte) 1))
                        .repository();
        UserAccount alice = UserAccount.create("alice", "first-password", "reader", true);
        repository.add(alice);
        assertEquals(2, repository.findAll().size());

        UserAccount disabledAlice = UserAccount.create(
                "alice", "second-password", "admin", false);
        repository.update(disabledAlice);
        UserAccount updated = repository.findByUsername("alice").orElseThrow();
        assertFalse(updated.enabled());
        assertEquals("admin", updated.scope());
        assertTrue(updated.matches("second-password"));

        assertTrue(repository.delete("alice"));
        assertFalse(repository.findByUsername("alice").isPresent());
        assertFalse(repository.delete("alice"));
    }

    @Test
    void decryptsWithOldKeyAndRewritesWithActiveKey(@TempDir Path directory)
            throws Exception {
        Path usersFile = directory.resolve(FileUserRepository.FILE_NAME);
        byte[] oldKey = keyBytes((byte) 1);
        KeyRing initialKeys = new KeyRing("k1", Map.of("k1", oldKey));
        FileUserRepository.InitialAdmin initial = FileUserRepository
                .openOrCreate(usersFile, initialKeys).initialAdmin().orElseThrow();
        KeyRing rotatedKeys = new KeyRing("k2",
                Map.of("k1", oldKey, "k2", keyBytes((byte) 2)));

        FileUserRepository rotated = FileUserRepository
                .openOrCreate(usersFile, rotatedKeys).repository();
        assertTrue(rotated.findByUsername("admin").orElseThrow()
                .matches(initial.password()));
        rotated.add(UserAccount.create("reader", "reader-password", "reader", true));

        String encrypted = Files.readString(usersFile, StandardCharsets.UTF_8);
        assertTrue(encrypted.startsWith("arbiter:v1:aes-256-gcm:k2:"));
    }

    private static KeyRing keyRing(String id, byte value) {
        return new KeyRing(id, Map.of(id, keyBytes(value)));
    }

    private static byte[] keyBytes(byte value) {
        byte[] key = new byte[32];
        Arrays.fill(key, value);
        return key;
    }
}
