package io.github.arbiter.command.module.status;

import picocli.CommandLine.Model.CommandSpec;

import io.github.arbiter.command.AbstractCommand;
import io.github.arbiter.command.result.CommandResult;
import io.github.arbiter.command.result.payload.ServiceStatusPayload;
import io.github.arbiter.core.service.SystemService;

import java.util.function.Supplier;
import io.github.arbiter.core.service.SystemStatus;
import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

@Command(name = "status", mixinStandardHelpOptions = true, resourceBundle = "messages")
public final class ServiceStatusCommand extends AbstractCommand implements Callable<Integer> {
    @Spec
    private CommandSpec spec;

    private final Supplier<SystemService> service;

    public ServiceStatusCommand(Supplier<SystemService> service) {
        this.service = service;
    }

    @Override
    public Integer call() {
        SystemStatus status = service.get().status();
        ServiceStatusPayload payload = new ServiceStatusPayload(
                value(status.state()), value(status.version()), value(status.serverTime()));
        CommandResult result = CommandResult.success("cmd.status.success", payload,
                payload.state(), payload.version(), payload.serverTime());
        render(spec, result);
        return result.exitCode();
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
