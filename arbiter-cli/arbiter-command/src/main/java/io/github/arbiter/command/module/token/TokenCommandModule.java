package io.github.arbiter.command.module.token;

import io.github.arbiter.command.CommandModule;
import io.github.arbiter.core.input.SecretInput;
import io.github.arbiter.core.service.TokenService;
import picocli.CommandLine;

import java.util.function.Supplier;

public final class TokenCommandModule implements CommandModule {
    private final Supplier<TokenService> tokenService;
    private final SecretInput secretInput;

    public TokenCommandModule(Supplier<TokenService> tokenService,
                              SecretInput secretInput) {
        this.tokenService = tokenService;
        this.secretInput = secretInput;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public String name() {
        return "token";
    }

    @Override
    public CommandLine commandLine() {
        CommandLine token = new CommandLine(new TokenGroup());
        token.addSubcommand("create", new CommandLine(
                new TokenCreateCommand(tokenService, secretInput)));
        return token;
    }
}
