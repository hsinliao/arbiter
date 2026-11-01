package io.github.arbiter.command.module.user;

import io.github.arbiter.command.CommandModule;
import io.github.arbiter.core.input.SecretInput;
import io.github.arbiter.core.service.UserService;
import picocli.CommandLine;

import java.util.function.Supplier;

public final class UserCommandModule implements CommandModule {
    private final Supplier<UserService> userService;
    private final SecretInput secretInput;

    public UserCommandModule(Supplier<UserService> userService, SecretInput secretInput) {
        this.userService = userService;
        this.secretInput = secretInput;
    }

    @Override
    public int order() {
        return 25;
    }

    @Override
    public String name() {
        return "user";
    }

    @Override
    public CommandLine commandLine() {
        CommandLine user = new CommandLine(new UserGroup());
        user.addSubcommand("list", new CommandLine(new UserListCommand(userService)));
        user.addSubcommand("show", new CommandLine(new UserShowCommand(userService)));
        user.addSubcommand("create", new CommandLine(
                new UserCreateCommand(userService, secretInput)));
        user.addSubcommand("update", new CommandLine(
                new UserUpdateCommand(userService, secretInput)));
        user.addSubcommand("delete", new CommandLine(new UserDeleteCommand(userService)));
        return user;
    }
}
