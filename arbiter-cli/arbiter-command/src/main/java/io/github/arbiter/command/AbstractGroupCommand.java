package io.github.arbiter.command;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

/**
 * Shared behavior for command groups invoked without a subcommand: print the
 * group usage and return the usage exit code.
 */
public abstract class AbstractGroupCommand implements Callable<Integer> {
    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getOut());
        return 2;
    }
}
