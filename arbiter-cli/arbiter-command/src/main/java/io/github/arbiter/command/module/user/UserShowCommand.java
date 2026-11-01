package io.github.arbiter.command.module.user;

import io.github.arbiter.command.AbstractCommand;
import io.github.arbiter.command.result.CommandResult;
import io.github.arbiter.command.result.payload.UserPayload;
import io.github.arbiter.core.service.UserService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

@Command(name = "show", mixinStandardHelpOptions = true, resourceBundle = "messages")
public final class UserShowCommand extends AbstractCommand implements Callable<Integer> {
    @Spec
    private picocli.CommandLine.Model.CommandSpec spec;
    @Parameters(index = "0", arity = "0..1")
    private String username;
    private final Supplier<UserService> service;

    public UserShowCommand(Supplier<UserService> service) {
        this.service = service;
    }

    @Override
    public Integer call() {
        String name = resolveObjectName(spec, username, "prompt.user.username",
                "field.username");
        UserPayload payload = UserPayload.from(service.get().show(name));
        CommandResult result = CommandResult.success("cmd.user.show.success", payload,
                payload.username(), payload.scope(), payload.enabled());
        render(spec, result);
        return result.exitCode();
    }
}
