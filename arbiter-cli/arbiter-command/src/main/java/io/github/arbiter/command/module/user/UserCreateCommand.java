package io.github.arbiter.command.module.user;

import io.github.arbiter.command.AbstractCommand;
import io.github.arbiter.command.result.CommandResult;
import io.github.arbiter.command.result.payload.UserPayload;
import io.github.arbiter.core.input.SecretInput;
import io.github.arbiter.core.service.UserService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

@Command(name = "create", mixinStandardHelpOptions = true, resourceBundle = "messages")
public final class UserCreateCommand extends AbstractCommand implements Callable<Integer> {
    @Spec
    private picocli.CommandLine.Model.CommandSpec spec;
    @Parameters(index = "0", arity = "0..1")
    private String username;
    @Option(names = "--scope", defaultValue = "reader")
    private String scope;
    @Option(names = "--disable")
    private boolean disabled;
    @Option(names = "--password-stdin")
    private boolean passwordStdin;
    private final Supplier<UserService> service;
    private final SecretInput secretInput;

    public UserCreateCommand(Supplier<UserService> service, SecretInput secretInput) {
        this.service = service;
        this.secretInput = secretInput;
    }

    @Override
    public Integer call() {
        String name = resolveObjectName(spec, username, "prompt.user.username",
                "field.username");
        String password = interaction(spec).readSecret(secretInput, passwordStdin,
                "prompt.user.password");
        UserPayload payload = UserPayload.from(
                service.get().create(name, password, scope, !disabled));
        CommandResult result = CommandResult.success("cmd.user.create.success", payload,
                payload.username(), payload.scope());
        render(spec, result);
        return result.exitCode();
    }
}
