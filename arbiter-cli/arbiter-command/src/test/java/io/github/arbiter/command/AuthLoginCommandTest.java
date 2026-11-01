package io.github.arbiter.command;

import io.github.arbiter.api.config.ArbiterKeys;

import io.github.arbiter.core.exception.DomainException;
import io.github.arbiter.command.module.server.UnavailableServerControlService;
import io.github.arbiter.core.service.AuthService;
import io.github.arbiter.core.service.AuthStatus;
import io.github.arbiter.core.service.LoginResult;
import io.github.arbiter.core.service.LogoutResult;
import io.github.arbiter.core.service.SystemStatus;
import io.github.arbiter.core.service.TokenSource;
import io.github.arbiter.core.service.VerificationResult;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthLoginCommandTest {
    @Test
    void usesHiddenSecretInputWithoutLeakingIt() {
        AtomicReference<String> submitted = new AtomicReference<>();
        AuthService auth = new StubAuth() {
            @Override
            public LoginResult login(String username, String password) {
                submitted.set(password);
                return new LoginResult(username, "2030-01-01T00:00:00Z", false, false);
            }
        };
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        var command = CommandTestSupport.commandLine(
                CommandTestSupport.nonInteractive(),
                new StubSecretInput(true, "super-secret"),
                auth, () -> new SystemStatus("UP", "1", "now"),
                name -> null, out, err);

        int exit = command.execute("auth", "login", "--username", "alice", "--lang", "en");

        assertEquals(0, exit);
        assertEquals("super-secret", submitted.get());
        assertTrue(out.toString().contains("Login succeeded"));
        assertFalse(out.toString().contains("super-secret"));
        assertFalse(err.toString().contains("super-secret"));
    }

    @Test
    void passwordStdinWorksWithoutInteractiveTerminal() {
        AtomicReference<String> submitted = new AtomicReference<>();
        AuthService auth = new StubAuth() {
            @Override
            public LoginResult login(String username, String password) {
                submitted.set(password);
                return new LoginResult(username, null, false, false);
            }
        };
        var command = CommandTestSupport.commandLine(
                CommandTestSupport.nonInteractive(),
                new StubSecretInput(false, "stdin-secret"),
                auth, () -> new SystemStatus("UP", "1", "now"),
                name -> null, new StringWriter(), new StringWriter());

        int exit = command.execute("auth", "login", "--username", "alice",
                "--password-stdin", "--no-input", "--lang", "en");

        assertEquals(0, exit);
        assertEquals("stdin-secret", submitted.get());
    }

    @Test
    void usernameComesFromInjectedEnvironment() {
        AtomicReference<String> submitted = new AtomicReference<>();
        AuthService auth = new StubAuth() {
            @Override
            public LoginResult login(String username, String password) {
                submitted.set(username);
                return new LoginResult(username, null, false, false);
            }
        };
        var command = CommandTestSupport.commandLine(
                CommandTestSupport.nonInteractive(),
                new StubSecretInput(false, "stdin-secret"),
                auth, () -> new SystemStatus("UP", "1", "now"),
                name -> null, new UnavailableServerControlService(),
                name -> ArbiterKeys.Env.USERNAME.equals(name) ? "env-alice" : null,
                new StringWriter(), new StringWriter());

        int exit = command.execute("auth", "login", "--password-stdin",
                "--no-input", "--lang", "en");

        assertEquals(0, exit);
        assertEquals("env-alice", submitted.get());
    }

    @Test
    void environmentWarningDoesNotProduceTwoJsonDocuments() {
        AuthService auth = new StubAuth() {
            @Override
            public LoginResult login(String username, String password) {
                return new LoginResult(username, "2030-01-01", false, true);
            }
        };
        StringWriter out = new StringWriter();
        var command = CommandTestSupport.commandLine(
                CommandTestSupport.nonInteractive(),
                new StubSecretInput(false, "stdin-secret"),
                auth, () -> new SystemStatus("UP", "1", "now"),
                name -> null, out, new StringWriter());

        int exit = command.execute("auth", "login", "--username", "alice",
                "--password-stdin", "--output", "json", "--lang", "en");

        assertEquals(0, exit);
        assertEquals(1, out.toString().lines().filter(line -> !line.isBlank()).count());
        assertTrue(out.toString().contains("ARBITER_TOKEN is set"));
    }

    private abstract static class StubAuth implements AuthService {
        @Override
        public AuthStatus status() {
            return new AuthStatus(TokenSource.FILE, "sha256:00000000",
                    new VerificationResult(VerificationResult.State.INVALID,
                            null, null, null));
        }

        @Override
        public VerificationResult verify(TokenSource source) {
            return new VerificationResult(VerificationResult.State.INVALID,
                    null, null, null);
        }

        @Override
        public LogoutResult logout() {
            return new LogoutResult(false, false);
        }

        @Override
        public boolean environmentTokenPresent() {
            return false;
        }
    }
}
