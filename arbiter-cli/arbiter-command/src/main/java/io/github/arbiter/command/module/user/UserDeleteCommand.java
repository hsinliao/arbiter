package io.github.arbiter.command.module.user;

import io.github.arbiter.command.AbstractCommand;
import io.github.arbiter.command.result.CommandResult;
import io.github.arbiter.core.exception.UsageException;
import io.github.arbiter.core.service.UserService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

@Command(name = "delete", mixinStandardHelpOptions = true, resourceBundle = "messages")
public final class UserDeleteCommand extends AbstractCommand implements Callable<Integer> {
    @Spec
    private picocli.CommandLine.Model.CommandSpec spec;
    @Parameters(index = "0", arity = "0..1")
    private String username;
    @Option(names = {"--yes", "-y"})
    private boolean confirmed;
    private final Supplier<UserService> service;

    public UserDeleteCommand(Supplier<UserService> service) {
        this.service = service;
    }

    @Override
    public Integer call() {
        if (!confirmed) {
            throw new UsageException("err.user.deleteConfirmation");
        }
        String name = resolveObjectName(spec, username, "prompt.user.username",
                "field.username");
        service.get().delete(name);
        CommandResult result = CommandResult.success("cmd.user.delete.success",
                Map.of("username", name), name);
        render(spec, result);
        return result.exitCode();
    }
}
