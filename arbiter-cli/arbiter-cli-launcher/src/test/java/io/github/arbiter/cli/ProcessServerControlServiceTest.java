package io.github.arbiter.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessServerControlServiceTest {
    @TempDir
    Path appHome;

    @Test
    void livePidWithoutLockIsNotARunningServer() throws Exception {
        Path run = Files.createDirectories(appHome.resolve("run"));
        Files.writeString(run.resolve(".server.pid"),
                Long.toString(ProcessHandle.current().pid()), StandardCharsets.UTF_8);

        var service = new ProcessServerControlService(appHome);

        assertFalse(service.status().running());
    }

    @Test
    void heldLockIsAuthoritativeAndProvidesThePid() throws Exception {
        Path run = Files.createDirectories(appHome.resolve("run"));
        Path lockFile = run.resolve(".server.lock");
        long pid = ProcessHandle.current().pid();
        Files.writeString(lockFile, Long.toString(pid), StandardCharsets.UTF_8);
        Files.writeString(run.resolve(".server.pid"), Long.toString(pid),
                StandardCharsets.UTF_8);

        try (FileChannel channel = FileChannel.open(lockFile,
                StandardOpenOption.READ, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            var status = new ProcessServerControlService(appHome).status();
            assertTrue(status.running());
            assertEquals(pid, status.pid());
        }
    }
}
