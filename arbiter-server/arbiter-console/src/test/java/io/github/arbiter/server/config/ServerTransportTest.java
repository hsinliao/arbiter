package io.github.arbiter.server.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerTransportTest {
    @Test
    void parsesBlankAndMixedCaseValues() {
        assertEquals(ServerTransport.Mode.HTTP, ServerTransport.parseMode(null));
        assertEquals(ServerTransport.Mode.HTTP, ServerTransport.parseMode("  "));
        assertEquals(ServerTransport.Mode.HTTP, ServerTransport.parseMode("http"));
        assertEquals(ServerTransport.Mode.UNIX, ServerTransport.parseMode("UNIX"));
        assertEquals(ServerTransport.Mode.BOTH, ServerTransport.parseMode(" both "));
    }

    @Test
    void rejectsUnknownValues() {
        assertThrows(IllegalArgumentException.class,
                () -> ServerTransport.parseMode("ftp"));
    }
}
