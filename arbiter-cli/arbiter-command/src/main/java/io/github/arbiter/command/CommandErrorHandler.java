package io.github.arbiter.command;

import io.github.arbiter.core.exception.DomainException;
import io.github.arbiter.core.exception.RemoteException;
import io.github.arbiter.core.exception.UsageException;
import picocli.CommandLine;

import java.util.regex.Pattern;

/**
 * The single error-rendering policy for both direct CLI and interactive shell
 * execution. Keeping it in the command module prevents the two entry points
 * from drifting in exit codes, JSON routing, or password redaction.
 */
public final class CommandErrorHandler {
    private static final Pattern PASSWORD_OPTION = Pattern.compile(
            "(^|\\s|['\"])--password(?=$|[\\s'\"=,])");

    private CommandErrorHandler() {
    }

    public static void install(CommandLine commandLine,
                               boolean printUsageOnParameterError) {
        commandLine.setParameterExceptionHandler((exception, args) -> {
            String details = exception.getMessage();
            if (details != null && PASSWORD_OPTION.matcher(details).find()) {
                render(commandLine, new UsageException("err.usage.password-option"));
            } else {
                render(commandLine, new UsageException("err.usage.invalid"));
            }
            if (printUsageOnParameterError) {
                commandLine.usage(commandLine.getErr());
            }
            return 2;
        });
        commandLine.setExecutionExceptionHandler((exception, command, parseResult) -> {
            DomainException error = exception instanceof DomainException domain
                    ? domain : new RemoteException(null, null);
            render(commandLine, error);
            if (error.exitCode() == 2 && printUsageOnParameterError) {
                commandLine.usage(commandLine.getErr());
            }
            return error.exitCode();
        });
        commandLine.setExitCodeExceptionMapper(exception ->
                exception instanceof DomainException domain ? domain.exitCode() : 1);
    }

    public static void render(CommandLine commandLine, DomainException error) {
        CliRoot root = CliRoot.from(commandLine.getCommandSpec());
        var writer = root.renderOptions().json()
                ? commandLine.getOut() : commandLine.getErr();
        root.renderer().renderError(error, root.renderOptions(), writer);
        commandLine.getErr().flush();
        commandLine.getOut().flush();
    }
}
