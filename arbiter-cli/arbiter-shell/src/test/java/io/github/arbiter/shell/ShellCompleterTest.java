package io.github.arbiter.shell;

import io.github.arbiter.command.CommandRegistry;
import io.github.arbiter.command.RuntimeSignals;
import io.github.arbiter.command.result.ResultRenderer;
import io.github.arbiter.core.input.InputPrompter;
import io.github.arbiter.core.input.SecretInput;
import io.github.arbiter.core.service.AuthService;
import io.github.arbiter.core.service.AuthStatus;
import io.github.arbiter.command.CommandContext;
import io.github.arbiter.command.module.auth.AuthCommandModule;
import io.github.arbiter.command.module.query.QueryCommandModule;
import io.github.arbiter.command.module.server.ServerCommandModule;
import io.github.arbiter.command.module.server.UnavailableServerControlService;
import io.github.arbiter.command.module.status.ServiceStatusCommandModule;
import io.github.arbiter.core.service.LoginResult;
import io.github.arbiter.core.service.LogoutResult;
import io.github.arbiter.core.service.QueryResult;
import io.github.arbiter.core.service.SystemStatus;
import io.github.arbiter.core.service.TokenSource;
import io.github.arbiter.core.service.VerificationResult;
import org.jline.reader.Candidate;
import org.jline.reader.ParsedLine;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.shell.jline3.PicocliJLineCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShellCompleterTest {
    @Test
    void completesCommandsAndOptionsFromTheSharedTree() {
        CommandContext context = new CommandContext(new StubInput(),
                new ResultRenderer(Map.<String, String>of()::get, Locale.ENGLISH),
                new RuntimeSignals(), name -> null, null);
        CommandLine commandLine = CommandRegistry.build(context, List.of(
                new AuthCommandModule(() -> new StubAuth(), new StubSecret()),
                new ServiceStatusCommandModule(() -> () -> new SystemStatus("UP", "1", "now")),
                new QueryCommandModule(() -> name -> new QueryResult(name, "ok")),
                new ServerCommandModule(new UnavailableServerControlService())));
        PicocliJLineCompleter completer = new PicocliJLineCompleter(
                commandLine.getCommandSpec());

        List<Candidate> commandCandidates = new ArrayList<>();
        completer.complete(null, parsedLine(List.of("sta"), 0, "sta"), commandCandidates);
        assertTrue(commandCandidates.stream()
                .anyMatch(candidate -> "status".equals(candidate.value())));

        List<Candidate> optionCandidates = new ArrayList<>();
        completer.complete(null,
                parsedLine(List.of("status", "--ver"), 1, "--ver"), optionCandidates);
        assertTrue(optionCandidates.stream()
                .anyMatch(candidate -> "--verbose".equals(candidate.value())));
    }

    private static ParsedLine parsedLine(List<String> words, int wordIndex, String word) {
        String line = String.join(" ", words);
        return new ParsedLine() {
            @Override
            public String word() {
                return word;
            }

            @Override
            public int wordCursor() {
                return word.length();
            }

            @Override
            public int wordIndex() {
                return wordIndex;
            }

            @Override
            public List<String> words() {
                return words;
            }

            @Override
            public String line() {
                return line;
            }

            @Override
            public int cursor() {
                return line.length();
            }
        };
    }

    private static final class StubInput implements InputPrompter {
        @Override
        public String readLine(String promptKey, Object... args) {
            return null;
        }

        @Override
        public boolean isInteractive() {
            return false;
        }
    }

    private static final class StubSecret implements SecretInput {
        @Override
        public String readSecret(String promptKey) {
            return null;
        }

        @Override
        public String readFromStdin() {
            return null;
        }

        @Override
        public boolean isInteractive() {
            return false;
        }
    }

    private static final class StubAuth implements AuthService {
        @Override
        public LoginResult login(String username, String password) {
            return new LoginResult(username, null, false, false);
        }

        @Override
        public AuthStatus status() {
            return new AuthStatus(TokenSource.NONE, "sha256:00000000",
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
