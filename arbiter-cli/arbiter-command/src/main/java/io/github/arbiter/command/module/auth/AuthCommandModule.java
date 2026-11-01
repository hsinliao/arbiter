package io.github.arbiter.command.module.auth;

import io.github.arbiter.command.CommandModule;
import io.github.arbiter.core.input.SecretInput;
import io.github.arbiter.core.service.AuthService;

import java.util.function.Supplier;
import picocli.CommandLine;

public final class AuthCommandModule implements CommandModule {
    private final Supplier<AuthService> authService;
    private final SecretInput secretInput;

    public AuthCommandModule(Supplier<AuthService> authService, SecretInput secretInput) {
        this.authService = authService;
        this.secretInput = secretInput;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public String name() {
        return "auth";
    }

    @Override
    public CommandLine commandLine() {
        CommandLine auth = new CommandLine(new AuthGroup());
        auth.addSubcommand("login", new CommandLine(
                new AuthLoginCommand(authService, secretInput)));
        auth.addSubcommand("status", new CommandLine(new AuthStatusCommand(authService)));
        auth.addSubcommand("logout", new CommandLine(new AuthLogoutCommand(authService)));
        return auth;
    }
}
