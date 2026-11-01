package io.github.arbiter.server.control;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerControlTest {
    @Test
    void commonPropertiesLoadBeforeServerProperties(@TempDir Path home) throws Exception {
        String previous = System.getProperty("arbiter.home");
        Path conf = Files.createDirectories(home.resolve("conf"));
        write(conf.resolve("common.properties"), "arbiter.api.prefix=/shared\n");
        write(conf.resolve("server.properties"), "arbiter.api.prefix=/server\n");
        System.setProperty("arbiter.home", home.toString());
        try {
            assertEquals("/server", ServerPaths.loadProperties(ServerPaths.under(home))
                    .getProperty("arbiter.api.prefix"));
        } finally {
            restoreHome(previous);
        }
    }

    @Test
    void parsesOperationalFlags() {
        ServerControl.Options check = ServerControl.parseArgs(new String[]{"--check"});
        assertTrue(check.check());
        assertFalse(check.stop());

        ServerControl.Options stop = ServerControl.parseArgs(new String[]{"--stop"});
        assertTrue(stop.stop());
        assertFalse(stop.check());
    }

    @Test
    void supportsHelpAndVersionWithoutStartingTheServer() {
        assertTrue(ServerControl.parseArgs(new String[]{"--help"}).help());
        assertTrue(ServerControl.parseArgs(new String[]{"-h"}).help());
        assertTrue(ServerControl.parseArgs(new String[]{"--version"}).version());
        assertTrue(ServerControl.parseArgs(new String[]{"-V"}).version());
    }

    @Test
    void rejectsRemovedAndUnknownOptions() {
        assertThrows(ServerControl.UsageException.class,
                () -> ServerControl.parseArgs(new String[]{"--unknown"}));
        assertThrows(ServerControl.UsageException.class,
                () -> ServerControl.parseArgs(new String[]{"--config", "/tmp/server.properties"}));
        assertThrows(ServerControl.UsageException.class,
                () -> ServerControl.parseArgs(new String[]{"--pid-file", "/tmp/server.pid"}));
        assertThrows(ServerControl.UsageException.class,
                () -> ServerControl.parseArgs(new String[]{"--port", "9090"}));
        assertThrows(ServerControl.UsageException.class,
                () -> ServerControl.parseArgs(new String[]{"status"}));
    }

    @Test
    void usageErrorReturnsExitCodeTwo() {
        assertEquals(2, new ServerControl().run(new String[]{"--unknown"}));
    }

    @Test
    void checkReportsStoppedAndStopIsIdempotent(@TempDir Path home) {
        String previous = System.getProperty("arbiter.home");
        System.setProperty("arbiter.home", home.toString());
        try {
            assertEquals(3, new ServerControl().run(new String[]{"--check"}));
            assertEquals(0, new ServerControl().run(new String[]{"--stop"}));
        } finally {
            restoreHome(previous);
        }
    }

    @Test
    void checkAndStopRequireArbiterHome() {
        String previous = System.getProperty("arbiter.home");
        System.clearProperty("arbiter.home");
        try {
            assertEquals(2, new ServerControl().run(new String[]{"--check"}));
            assertEquals(2, new ServerControl().run(new String[]{"--stop"}));
        } finally {
            restoreHome(previous);
        }
    }

    private static void write(Path path, String content) throws IOException {
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void restoreHome(String previous) {
        if (previous == null) {
            System.clearProperty("arbiter.home");
        } else {
            System.setProperty("arbiter.home", previous);
        }
    }
}
