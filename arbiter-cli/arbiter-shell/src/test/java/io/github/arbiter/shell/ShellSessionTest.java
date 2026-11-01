package io.github.arbiter.shell;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShellSessionTest {
    @Test
    void recognizesExitCommands() {
        assertTrue(ShellSession.isExitCommand("exit"));
        assertTrue(ShellSession.isExitCommand(" EXIT "));
        assertTrue(ShellSession.isExitCommand("quit"));
        assertTrue(ShellSession.isExitCommand(":q"));
        assertTrue(ShellSession.isExitCommand(":quit"));
    }

    @Test
    void doesNotTreatOtherCommandsAsExit() {
        assertFalse(ShellSession.isExitCommand(null));
        assertFalse(ShellSession.isExitCommand("exiting"));
        assertFalse(ShellSession.isExitCommand("exit now"));
        assertFalse(ShellSession.isExitCommand("status"));
    }
}
