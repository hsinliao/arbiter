package io.github.arbiter.command.module.query;

import io.github.arbiter.command.CommandModule;
import io.github.arbiter.core.service.QueryService;

import java.util.function.Supplier;
import picocli.CommandLine;

public final class QueryCommandModule implements CommandModule {
    private final Supplier<QueryService> queryService;

    public QueryCommandModule(Supplier<QueryService> queryService) {
        this.queryService = queryService;
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public String name() {
        return "query";
    }

    @Override
    public CommandLine commandLine() {
        return new CommandLine(new QueryCommand(queryService));
    }
}
