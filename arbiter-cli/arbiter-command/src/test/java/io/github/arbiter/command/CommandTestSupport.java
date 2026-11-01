package io.github.arbiter.command;

import io.github.arbiter.core.input.InputPrompter;
import io.github.arbiter.core.input.SecretInput;
import io.github.arbiter.command.result.ResultRenderer;
import io.github.arbiter.core.service.AuthService;
import io.github.arbiter.core.service.AuthStatus;
import io.github.arbiter.command.module.auth.AuthCommandModule;
import io.github.arbiter.command.module.query.QueryCommandModule;
import io.github.arbiter.command.module.server.ServerCommandModule;
import io.github.arbiter.command.module.server.UnavailableServerControlService;
import io.github.arbiter.command.module.status.ServiceStatusCommandModule;
import io.github.arbiter.command.module.user.UserCommandModule;
import io.github.arbiter.core.service.LoginResult;
import io.github.arbiter.core.service.LogoutResult;
import io.github.arbiter.core.service.QueryService;
import io.github.arbiter.core.service.ServerControlService;
import io.github.arbiter.core.service.SystemService;
import io.github.arbiter.core.service.TokenSource;
import io.github.arbiter.core.service.UserInfo;
import io.github.arbiter.core.service.UserService;
import io.github.arbiter.core.service.VerificationResult;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

final class CommandTestSupport {
    private CommandTestSupport() {
    }

    static CommandLine commandLine(InputPrompter input, SecretInput secret,
                                   AuthService auth, SystemService system,
                                   QueryService query, StringWriter out,
                                   StringWriter err) {
        return commandLine(input, secret, auth, system, query,
                new UnavailableServerControlService(), name -> null, out, err);
    }

    static CommandLine commandLine(InputPrompter input, SecretInput secret,
                                   AuthService auth, SystemService system,
                                   QueryService query, ServerControlService serverControl,
                                   StringWriter out, StringWriter err) {
        return commandLine(input, secret, auth, system, query, serverControl,
                name -> null, out, err);
    }

    static CommandLine commandLine(InputPrompter input, SecretInput secret,
                                   AuthService auth, SystemService system,
                                   QueryService query, ServerControlService serverControl,
                                   Function<String, String> environment,
                                   StringWriter out, StringWriter err) {
        return commandLine(input, secret, List.of(
                new AuthCommandModule(() -> auth, secret),
                new ServiceStatusCommandModule(() -> system),
                new QueryCommandModule(() -> query),
                new UserCommandModule(CommandTestSupport::noOpUser, secret),
                new ServerCommandModule(serverControl)), environment, out, err);
    }

    /**
     * Feature-level fixture: pass only the modules under test instead of the
     * whole command tree.
     */
    static CommandLine commandLine(InputPrompter input, SecretInput secret,
                                   List<CommandModule> modules,
                                   Function<String, String> environment,
                                   StringWriter out, StringWriter err) {
        ResultRenderer renderer = new ResultRenderer(Map.<String, String>of()::get,
                Locale.ENGLISH);
        CommandContext context = new CommandContext(input, renderer, new RuntimeSignals(),
                environment, null);
        CommandLine commandLine = CommandRegistry.build(context, modules);
        commandLine.setOut(new PrintWriter(out, true));
        commandLine.setErr(new PrintWriter(err, true));
        CommandErrorHandler.install(commandLine, false);
        return commandLine;
    }

    static AuthService noOpAuth() {
        return new AuthService() {
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
        };
    }

    static UserService noOpUser() {
        return new UserService() {
            @Override
            public List<UserInfo> list() {
                return List.of();
            }

            @Override
            public UserInfo show(String username) {
                return new UserInfo(username, "reader", true);
            }

            @Override
            public UserInfo create(String username, String password, String scope,
                                   boolean enabled) {
                return new UserInfo(username, scope, enabled);
            }

            @Override
            public UserInfo update(String username, String password, String scope,
                                   Boolean enabled) {
                return new UserInfo(username, scope == null ? "reader" : scope,
                        enabled == null || enabled);
            }

            @Override
            public void delete(String username) {
            }
        };
    }

    static StubInputPrompter nonInteractive() {
        return new StubInputPrompter(false);
    }

    static StubSecretInput noSecret() {
        return new StubSecretInput(false);
    }
}
