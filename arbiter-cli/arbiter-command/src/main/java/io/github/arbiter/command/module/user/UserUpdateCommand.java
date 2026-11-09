package io.github.arbiter.command.module.user;

import picocli.CommandLine.Model.CommandSpec;

import io.github.arbiter.command.AbstractCommand;
import io.github.arbiter.command.result.CommandResult;
import io.github.arbiter.command.result.payload.UserPayload;
import io.github.arbiter.core.exception.UsageException;
import io.github.arbiter.core.input.SecretInput;
import io.github.arbiter.core.service.UserService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

@Command(name = "update", mixinStandardHelpOptions = true, resourceBundle = "messages")
public final class UserUpdateCommand extends AbstractCommand implements Callable<Integer> {
    @Spec
    private CommandSpec spec;
    @Parameters(index = "0", arity = "0..1")
    private String username;
    @Option(names = "--scope")
    private String scope;
    @Option(names = "--enable")
    private boolean enable;
    @Option(names = "--disable")
    private boolean disable;
    @Option(names = "--password-stdin")
    private boolean passwordStdin;
    private final Supplier<UserService> service;
    private final SecretInput secretInput;

    public UserUpdateCommand(Supplier<UserService> service, SecretInput secretInput) {
        this.service = service;
        this.secretInput = secretInput;
    }

    @Override
    public Integer call() {
        if (enable && disable) {
            throw new UsageException("err.user.enableDisableConflict");
        }
        String name = resolveObjectName(spec, username, "prompt.user.username",
                "field.username");
        String password = passwordStdin
                ? interaction(spec).readSecret(secretInput, true, "prompt.user.password")
                : null;
        Boolean enabled = enable ? Boolean.TRUE : disable ? Boolean.FALSE : null;
        UserPayload payload = UserPayload.from(
                service.get().update(name, password, scope, enabled));
        CommandResult result = CommandResult.success("cmd.user.update.success", payload,
                payload.username(), payload.scope(), payload.enabled());
        render(spec, result);
        return result.exitCode();
    }
}
