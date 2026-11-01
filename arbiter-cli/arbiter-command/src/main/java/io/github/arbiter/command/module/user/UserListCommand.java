package io.github.arbiter.command.module.user;

import io.github.arbiter.command.AbstractCommand;
import io.github.arbiter.command.result.CommandResult;
import io.github.arbiter.command.result.payload.UserListPayload;
import io.github.arbiter.command.result.payload.UserPayload;
import io.github.arbiter.core.service.UserService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

@Command(name = "list", mixinStandardHelpOptions = true, resourceBundle = "messages")
public final class UserListCommand extends AbstractCommand implements Callable<Integer> {
    @Spec
    private picocli.CommandLine.Model.CommandSpec spec;
    private final Supplier<UserService> service;

    public UserListCommand(Supplier<UserService> service) {
        this.service = service;
    }

    @Override
    public Integer call() {
        UserListPayload payload = new UserListPayload(service.get().list().stream()
                .map(UserPayload::from).toList());
        String summary = payload.users().stream()
                .map(user -> user.username() + "(" + user.scope() + ", "
                        + (user.enabled() ? "enabled" : "disabled") + ")")
                .reduce((left, right) -> left + ", " + right).orElse("none");
        CommandResult result = CommandResult.success(
                "cmd.user.list.success", payload, summary);
        render(spec, result);
        return result.exitCode();
    }
}
