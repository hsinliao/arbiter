package io.github.arbiter.server.auth;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerKeyDerivationTest {
    @Test
    void derivesStablePurposeSeparatedKeys() {
        byte[] master = new byte[32];
        Arrays.fill(master, (byte) 7);

        byte[] first = ServerKeyDerivation.userStore(master);
        byte[] repeated = ServerKeyDerivation.userStore(master);
        byte[] token = ServerKeyDerivation.tokenSigning(master);
        byte[] agent = ServerKeyDerivation.agentState(master);

        assertTrue(Arrays.equals(first, repeated));
        assertFalse(Arrays.equals(first, token));
        assertFalse(Arrays.equals(first, agent));
        assertFalse(Arrays.equals(token, agent));
    }
}
