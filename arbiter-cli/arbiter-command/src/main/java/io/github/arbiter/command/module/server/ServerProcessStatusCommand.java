package io.github.arbiter.command.module.server;

import io.github.arbiter.command.AbstractCommand;
import io.github.arbiter.command.result.CommandResult;
import io.github.arbiter.command.result.payload.ServerStatusPayload;
import io.github.arbiter.core.service.ManagedServerStatus;
import io.github.arbiter.core.service.ServerControlService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

@Command(name = "status", mixinStandardHelpOptions = true, resourceBundle = "messages")
public final class ServerProcessStatusCommand extends AbstractCommand implements Callable<Integer> {
    @Spec
    private picocli.CommandLine.Model.CommandSpec spec;

    private final ServerControlService service;

    public ServerProcessStatusCommand(ServerControlService service) {
        this.service = service;
    }

    @Override
    public Integer call() {
        ManagedServerStatus status = service.status();
        ServerStatusPayload payload = new ServerStatusPayload(
                status.running(), status.pid());
        CommandResult result = CommandResult.success(
                status.running() ? "cmd.server.status.running" : "cmd.server.status.stopped",
                payload, Long.toString(payload.pid()));
        render(spec, result);
        return result.exitCode();
    }
}
