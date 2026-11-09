package io.github.arbiter.command.module.server;

import picocli.CommandLine.Model.CommandSpec;

import io.github.arbiter.command.AbstractCommand;
import io.github.arbiter.command.result.CommandResult;
import io.github.arbiter.command.result.payload.ServerOperationPayload;
import io.github.arbiter.core.service.ServerControlService;
import io.github.arbiter.core.service.ServerOperationResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

@Command(name = "restart", mixinStandardHelpOptions = true, resourceBundle = "messages")
public final class ServerRestartCommand extends AbstractCommand implements Callable<Integer> {
    @Spec
    private CommandSpec spec;

    @Option(names = "--debug",
            arity = "0..1",
            fallbackValue = "127.0.0.1:5005",
            paramLabel = "ADDRESS",
            description = "Restart the server with a JDWP agent on ADDRESS")
    private String debugAddress;

    private final ServerControlService service;

    public ServerRestartCommand(ServerControlService service) {
        this.service = service;
    }

    @Override
    public Integer call() {
        ServerOperationResult operation = service.restart(debugAddress);
        ServerOperationPayload payload = new ServerOperationPayload(
                operation.action(), operation.pid(), operation.alreadyRunning());
        CommandResult result = CommandResult.success("cmd.server.restart.success",
                payload, Long.toString(payload.pid()));
        render(spec, result);
        return result.exitCode();
    }
}
