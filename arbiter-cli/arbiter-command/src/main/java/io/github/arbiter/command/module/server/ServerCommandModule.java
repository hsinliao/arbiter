package io.github.arbiter.command.module.server;

import io.github.arbiter.command.CommandModule;
import io.github.arbiter.core.service.ServerControlService;
import picocli.CommandLine;

public final class ServerCommandModule implements CommandModule {
    private final ServerControlService serverControlService;

    public ServerCommandModule(ServerControlService serverControlService) {
        this.serverControlService = serverControlService;
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public String name() {
        return "server";
    }

    @Override
    public CommandLine commandLine() {
        CommandLine serverGroup = new CommandLine(new ServerGroup());
        serverGroup.addSubcommand("start", new CommandLine(
                new ServerStartCommand(serverControlService)));
        serverGroup.addSubcommand("status", new CommandLine(
                new ServerProcessStatusCommand(serverControlService)));
        serverGroup.addSubcommand("stop", new CommandLine(
                new ServerStopCommand(serverControlService)));
        serverGroup.addSubcommand("restart", new CommandLine(
                new ServerRestartCommand(serverControlService)));
        return serverGroup;
    }

}
