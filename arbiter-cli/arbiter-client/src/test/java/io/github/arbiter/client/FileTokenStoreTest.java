package io.github.arbiter.client;

import io.github.arbiter.client.token.FileTokenStore;
import io.github.arbiter.client.token.StoredToken;
import io.github.arbiter.core.exception.LocalStorageException;
import io.github.arbiter.core.service.TokenSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileTokenStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsAndDeletesOnlyTokenAndMetadata() {
        FileTokenStore store = new FileTokenStore(
                temporaryDirectory.resolve("credentials"), () -> null);

        store.save(new StoredToken("issued-token", "alice",
                "2030-01-01", "read"));

        var resolved = store.resolve().orElseThrow();
        assertEquals(TokenSource.FILE, resolved.source());
        assertEquals("issued-token", resolved.token().token());
        assertEquals("alice", resolved.token().username());
        assertTrue(store.delete());
        assertFalse(store.exists());
    }

    @Test
    void corruptCredentialFileIsReportedAsLocalStorageFailure() throws IOException {
        Path credentials = temporaryDirectory.resolve("credentials");
        Files.writeString(credentials, "{not-json");
        FileTokenStore store = new FileTokenStore(credentials, () -> null);

        assertThrows(LocalStorageException.class, store::resolve);
    }

    @Test
    void environmentTokenOverridesFile() {
        AtomicReference<String> environment = new AtomicReference<>("env-token");
        FileTokenStore store = new FileTokenStore(
                temporaryDirectory.resolve("credentials"), environment::get);
        store.save(new StoredToken("file-token", "alice", null, null));

        assertEquals(TokenSource.ENV, store.resolve().orElseThrow().source());
        assertEquals("env-token", store.resolve().orElseThrow().token().token());
    }
}
