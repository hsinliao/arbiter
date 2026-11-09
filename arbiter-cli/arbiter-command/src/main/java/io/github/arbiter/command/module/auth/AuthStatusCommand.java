package io.github.arbiter.command.module.auth;

import picocli.CommandLine.Model.CommandSpec;

import io.github.arbiter.command.AbstractCommand;
import io.github.arbiter.command.CliRoot;
import io.github.arbiter.command.result.CommandResult;
import io.github.arbiter.command.result.LocalizedArgument;
import io.github.arbiter.core.service.AuthStatus;
import io.github.arbiter.core.service.AuthService;

import java.util.function.Supplier;
import io.github.arbiter.core.service.TokenSource;
import io.github.arbiter.core.service.VerificationResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "status", mixinStandardHelpOptions = true, resourceBundle = "messages")
public final class AuthStatusCommand extends AbstractCommand implements Callable<Integer> {
    @Spec
    private CommandSpec spec;

    private final Supplier<AuthService> service;

    public AuthStatusCommand(Supplier<AuthService> service) {
        this.service = service;
    }

    @Override
    public Integer call() {
        CliRoot root = CliRoot.from(spec);
        AuthStatus status = service.get().status();
        VerificationResult verification = status.verification();
        Map<String, Object> data = baseData(status);
        data.put("valid", verification.state() == VerificationResult.State.VALID);
        data.put("verification", verification.state().name());
        data.put("subject", nullToEmpty(verification.subject()));
        data.put("expiresAt", nullToEmpty(verification.expiresAt()));

        CommandResult result;
        if (verification.state() == VerificationResult.State.VALID) {
            result = CommandResult.success("cmd.auth.status.valid", data,
                    sourceArg(status.source()),
                    nullToEmpty(status.tokenHint()),
                    nullToEmpty(verification.subject()),
                    nullToEmpty(verification.expiresAt()));
        } else if (verification.state() == VerificationResult.State.INVALID) {
            result = new CommandResult(false, "cmd.auth.status.invalid", new Object[]{
                    sourceArg(status.source()), nullToEmpty(status.tokenHint())
            }, data, 3);
        } else {
            if (verification.error() != null && root.renderOptions().verbose()) {
                data.put("serverCode", nullToEmpty(verification.error().serverCode()));
                data.put("serverMessage", nullToEmpty(verification.error().serverMessage()));
            }
            int exitCode = verification.error() == null ? 1 : verification.error().exitCode();
            result = new CommandResult(false, "cmd.auth.status.unverifiable", new Object[]{
                    sourceArg(status.source()),
                    nullToEmpty(status.tokenHint())
            }, data, exitCode);
        }
        render(spec, result);
        return result.exitCode();
    }

    private static Map<String, Object> baseData(AuthStatus status) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tokenSource", status.source().name());
        data.put("tokenHint", nullToEmpty(status.tokenHint()));
        return data;
    }

    private static LocalizedArgument sourceArg(TokenSource source) {
        return new LocalizedArgument("token.source." + source.name().toLowerCase(),
                new Object[0]);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
