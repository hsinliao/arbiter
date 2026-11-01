package io.github.arbiter.command.module.server;

import io.github.arbiter.command.AbstractCommand;
import io.github.arbiter.command.result.CommandResult;
import io.github.arbiter.core.service.ServerControlService;
import io.github.arbiter.core.service.ServerOperationResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;

import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "stop", mixinStandardHelpOptions = true, resourceBundle = "messages")
public final class ServerStopCommand extends AbstractCommand implements Callable<Integer> {
    @Spec
    private picocli.CommandLine.Model.CommandSpec spec;

    private final ServerControlService service;

    public ServerStopCommand(ServerControlService service) {
        this.service = service;
    }

    @Override
    public Integer call() {
        ServerOperationResult operation = service.stop();
        CommandResult result = CommandResult.success(
                operation.pid() > 0 ? "cmd.server.stop.success" : "cmd.server.stop.stopped",
                Map.of("action", operation.action(), "pid", operation.pid()),
                Long.toString(operation.pid()));
        render(spec, result);
        return result.exitCode();
    }
}
