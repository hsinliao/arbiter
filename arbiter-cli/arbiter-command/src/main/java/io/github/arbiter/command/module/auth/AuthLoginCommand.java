package io.github.arbiter.command.module.auth;

import picocli.CommandLine.Model.CommandSpec;

import io.github.arbiter.api.config.ArbiterKeys;

import io.github.arbiter.command.AbstractCommand;
import io.github.arbiter.command.CliRoot;
import io.github.arbiter.core.input.SecretInput;
import io.github.arbiter.command.result.CommandResult;
import io.github.arbiter.command.result.payload.LoginPayload;
import io.github.arbiter.core.service.AuthService;

import java.util.function.Supplier;
import io.github.arbiter.core.service.LoginResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

@Command(name = "login", mixinStandardHelpOptions = true, resourceBundle = "messages")
public final class AuthLoginCommand extends AbstractCommand implements Callable<Integer> {
    @Spec
    private CommandSpec spec;

    @Option(names = "--username")
    private String username;

    @Option(names = "--password-stdin")
    private boolean passwordStdin;

    private final Supplier<AuthService> service;
    private final SecretInput secretInput;

    public AuthLoginCommand(Supplier<AuthService> service, SecretInput secretInput) {
        this.service = service;
        this.secretInput = secretInput;
    }

    @Override
    public Integer call() {
        String resolvedUsername = resolveUsername();
        String password = interaction(spec).readSecret(
                secretInput, passwordStdin, "prompt.auth.password");

        LoginResult login = service.get().login(resolvedUsername, password);
        LoginPayload payload = new LoginPayload(nullToEmpty(login.username()),
                nullToEmpty(login.expiresAt()), login.updatedExisting());
        String key;
        if (login.updatedExisting() && login.environmentTokenPresent()) {
            key = "cmd.auth.login.updated.env";
        } else if (login.environmentTokenPresent()) {
            key = "cmd.auth.login.success.env";
        } else if (login.updatedExisting()) {
            key = "cmd.auth.login.updated";
        } else {
            key = "cmd.auth.login.success";
        }
        CommandResult result = CommandResult.success(key, payload,
                payload.username(), payload.expiresAt());
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
