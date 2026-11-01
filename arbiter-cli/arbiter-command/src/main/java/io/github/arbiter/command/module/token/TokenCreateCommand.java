package io.github.arbiter.command.module.token;

import io.github.arbiter.command.AbstractCommand;
import io.github.arbiter.command.CliRoot;
import io.github.arbiter.command.result.CommandResult;
import io.github.arbiter.command.result.payload.TokenPayload;
import io.github.arbiter.api.config.ArbiterKeys;
import io.github.arbiter.core.input.SecretInput;
import io.github.arbiter.core.service.TokenIssueResult;
import io.github.arbiter.core.service.TokenService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

@Command(name = "create", mixinStandardHelpOptions = true, resourceBundle = "messages")
public final class TokenCreateCommand extends AbstractCommand implements Callable<Integer> {
    @Spec
    private picocli.CommandLine.Model.CommandSpec spec;

    @Option(names = "--username")
    private String username;

    @Option(names = "--password-stdin")
    private boolean passwordStdin;

    private final Supplier<TokenService> service;
    private final SecretInput secretInput;

    public TokenCreateCommand(Supplier<TokenService> service, SecretInput secretInput) {
        this.service = service;
        this.secretInput = secretInput;
    }

    @Override
    public Integer call() {
        String resolvedUsername = resolveUsername();
        String password = interaction(spec).readSecret(
                secretInput, passwordStdin, "prompt.auth.password");
        TokenIssueResult issued = service.get().issueToken(resolvedUsername, password);
        TokenPayload payload = new TokenPayload(issued.token(),
                nullToEmpty(issued.username()), nullToEmpty(issued.scope()),
                nullToEmpty(issued.expiresAt()));
        CommandResult result = CommandResult.success("cmd.token.create.success", payload,
                payload.username(), payload.expiresAt(), payload.token());
        render(spec, result);
        return result.exitCode();
    }

    private String resolveUsername() {
        CliRoot root = CliRoot.from(spec);
        String supplied = username;
        if (supplied == null || supplied.isBlank()) {
            supplied = root.environment(ArbiterKeys.Env.USERNAME);
        }
        return interaction(spec).requiredText(supplied, "prompt.auth.username",
                "field.username", null, "err.usage.invalidName");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
