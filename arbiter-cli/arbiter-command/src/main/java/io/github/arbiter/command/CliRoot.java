package io.github.arbiter.command;

import io.github.arbiter.core.input.InputPrompter;
import io.github.arbiter.command.result.RenderOptions;
import io.github.arbiter.command.result.ResultRenderer;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;
import java.util.function.Function;

@Command(
        name = "arbiter",
        mixinStandardHelpOptions = true,
        version = "arbiter",
        resourceBundle = "messages"
)
public final class CliRoot implements Callable<Integer> {
    public enum OutputFormat {
        table,
        json
    }

    @Option(names = "--config", scope = ScopeType.INHERIT)
    private String config;

    @Option(names = "--output", scope = ScopeType.INHERIT)
    private OutputFormat output = OutputFormat.table;

    private String language;

    @Option(names = "--no-input", scope = ScopeType.INHERIT)
    private boolean noInput;

    @Spec
    private CommandLine.Model.CommandSpec spec;

    private final InputPrompter inputPrompter;
    private final ResultRenderer renderer;
    private final RuntimeSignals runtimeSignals;
    private final Function<String, String> environment;
    private final String fixedResultLanguage;

    public CliRoot(InputPrompter inputPrompter, ResultRenderer renderer,
                   RuntimeSignals runtimeSignals, Function<String, String> environment,
                   String fixedResultLanguage) {
        this.inputPrompter = inputPrompter;
        this.renderer = renderer;
        this.runtimeSignals = runtimeSignals;
        this.environment = environment;
        this.fixedResultLanguage = fixedResultLanguage;
    }

    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getOut());
        return 2;
    }

    public String config() {
        return config;
    }

    public boolean verbose() {
        return runtimeSignals.verbose();
    }

    public boolean noInput() {
        return noInput;
    }

    public InputPrompter inputPrompter() {
        return inputPrompter;
    }

    public ResultRenderer renderer() {
        return renderer;
    }

    public String environment(String name) {
        return environment == null ? null : environment.apply(name);
    }

    public RenderOptions renderOptions() {
        String effectiveLanguage = fixedResultLanguage == null ? language : fixedResultLanguage;
        return new RenderOptions(output == null ? "table" : output.name(),
                effectiveLanguage, verbose());
    }

    /**
     * Clears state that must be scoped to one shell command. Picocli resets
     * annotated option fields, but values written by option methods (notably
     * verbose) would otherwise remain active for the rest of the shell.
     */
    public void resetInvocationState() {
        runtimeSignals.verbose(false);
        if (fixedResultLanguage == null) {
            language = null;
            runtimeSignals.locale(renderer.resolveLocale(
                    new RenderOptions("table", null, false)));
        }
    }

    @Option(names = "--verbose", scope = ScopeType.INHERIT)
    void setVerbose(boolean verbose) {
        runtimeSignals.verbose(verbose);
    }

    @Option(names = "--lang", scope = ScopeType.INHERIT)
    void setLanguage(String language) {
        this.language = language;
        String effective = fixedResultLanguage == null ? language : fixedResultLanguage;
        runtimeSignals.locale(renderer.resolveLocale(
                new RenderOptions("table", effective, verbose())));
    }

    public static CliRoot from(CommandLine.Model.CommandSpec commandSpec) {
        return (CliRoot) commandSpec.root().userObject();
    }
}
