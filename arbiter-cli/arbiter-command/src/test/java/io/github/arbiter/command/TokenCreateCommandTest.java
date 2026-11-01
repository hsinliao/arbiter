package io.github.arbiter.command;

import io.github.arbiter.command.module.token.TokenCommandModule;
import io.github.arbiter.core.service.TokenIssueResult;
import io.github.arbiter.core.service.TokenService;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenCreateCommandTest {
    @Test
    void printsATokenIssuedForTheCredentials() {
        TokenService service = (username, password) ->
                new TokenIssueResult("token-123", username, "reader",
                        "2030-01-01T00:00:00Z");
        StubSecretInput secret = new StubSecretInput(false, "secret-password");
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        picocli.CommandLine commandLine = CommandTestSupport.commandLine(
                CommandTestSupport.nonInteractive(), secret,
                List.of(new TokenCommandModule(() -> service, secret)),
                name -> null, out, err);

        int exit = commandLine.execute("token", "create",
                "--username", "alice", "--password-stdin");

        assertEquals(0, exit, err.toString());
        assertTrue(out.toString().contains("token-123"), out.toString());
        assertTrue(out.toString().contains("alice"), out.toString());
    }

    @Test
    void requiresAUsername() {
        TokenService service = (username, password) ->
                new TokenIssueResult("token-123", username, "reader", null);
        StubSecretInput secret = new StubSecretInput(false, "secret-password");
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        picocli.CommandLine commandLine = CommandTestSupport.commandLine(
                CommandTestSupport.nonInteractive(), secret,
                List.of(new TokenCommandModule(() -> service, secret)),
                name -> null, out, err);

        int exit = commandLine.execute("token", "create", "--password-stdin");

        assertEquals(2, exit, err.toString());
    }
}
