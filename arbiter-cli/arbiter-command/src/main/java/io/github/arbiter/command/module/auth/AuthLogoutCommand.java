package io.github.arbiter.command.module.auth;

import io.github.arbiter.command.AbstractCommand;
import io.github.arbiter.command.result.CommandResult;
import io.github.arbiter.core.service.AuthService;

import java.util.function.Supplier;
import io.github.arbiter.core.service.LogoutResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;

import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "logout", mixinStandardHelpOptions = true, resourceBundle = "messages")
public final class AuthLogoutCommand extends AbstractCommand implements Callable<Integer> {
    @Spec
    private picocli.CommandLine.Model.CommandSpec spec;

    private final Supplier<AuthService> service;

    public AuthLogoutCommand(Supplier<AuthService> service) {
        this.service = service;
    }

    @Override
    public Integer call() {
        LogoutResult logout = service.get().logout();
        String key;
        if (logout.removed() && logout.environmentTokenPresent()) {
            key = "cmd.auth.logout.success.env";
        } else if (!logout.removed() && logout.environmentTokenPresent()) {
            key = "cmd.auth.logout.absent.env";
        } else {
            key = logout.removed() ? "cmd.auth.logout.success" : "cmd.auth.logout.absent";
        }
        CommandResult result = CommandResult.success(
                key,
                Map.of("removed", logout.removed()));
        render(spec, result);
        return result.exitCode();
    }
}
