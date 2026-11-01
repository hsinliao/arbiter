package io.github.arbiter.command.module.status;

import io.github.arbiter.command.CommandModule;
import io.github.arbiter.core.service.SystemService;

import java.util.function.Supplier;
import picocli.CommandLine;

public final class ServiceStatusCommandModule implements CommandModule {
    private final Supplier<SystemService> systemService;

    public ServiceStatusCommandModule(Supplier<SystemService> systemService) {
        this.systemService = systemService;
    }

    @Override
    public String name() {
        return "status";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public CommandLine commandLine() {
        return new CommandLine(new ServiceStatusCommand(systemService));
    }
}
