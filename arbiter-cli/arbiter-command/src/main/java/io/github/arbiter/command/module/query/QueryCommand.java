package io.github.arbiter.command.module.query;

import picocli.CommandLine.Model.CommandSpec;

import io.github.arbiter.command.AbstractCommand;
import io.github.arbiter.command.result.CommandResult;
import io.github.arbiter.command.result.payload.QueryPayload;
import io.github.arbiter.core.service.QueryService;

import java.util.function.Supplier;
import io.github.arbiter.core.service.QueryResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

@Command(name = "query", mixinStandardHelpOptions = true, resourceBundle = "messages")
public final class QueryCommand extends AbstractCommand implements Callable<Integer> {
    @Spec
    private CommandSpec spec;

    @Option(names = {"--name", "-n"}, required = false)
    private String name;

    private final Supplier<QueryService> service;

    public QueryCommand(Supplier<QueryService> service) {
        this.service = service;
    }

    @Override
    public Integer call() {
        String resolved = resolveObjectName(spec, name,
                "prompt.query.name", "field.name");
        QueryResult queryResult = service.get().query(resolved);
        QueryPayload payload = new QueryPayload(queryResult.name(), queryResult.value());
        CommandResult result = CommandResult.success("cmd.query.success", payload,
                payload.name(), payload.value());
        render(spec, result);
        return result.exitCode();
    }
}
