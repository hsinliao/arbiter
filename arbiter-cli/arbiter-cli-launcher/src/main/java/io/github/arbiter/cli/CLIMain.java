package io.github.arbiter.cli;

import io.github.arbiter.command.i18n.I18n;
import io.github.arbiter.command.result.CommandResult;
import io.github.arbiter.command.result.RenderOptions;
import io.github.arbiter.command.result.ResultRenderer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

public final class CLIMain {
    private CLIMain() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.getenv(), Path.of("").toAbsolutePath(), System.out,
                System.err));
    }

    public static int run(String[] args, Map<String, String> environment, Path workingDirectory,
                          OutputStream stdout, OutputStream stderr) {
        Locale startupLocale = I18n.resolveStartupLocale(environment, Locale.getDefault());
        PrintWriter out = OutputEncoding.writer(stdout);
        PrintWriter err = OutputEncoding.writer(stderr);
        ResultRenderer renderer = new ResultRenderer(environment::get, Locale.getDefault());
        try {
            if (args.length == 0) {
                BootstrapSettings settings = BootstrapSettings.resolve(environment,
                        workingDirectory);
                return CliApplication.runShell(settings, startupLocale, environment,
                        workingDirectory, stdout, stderr);
            }
            return CliApplication.runCli(args, environment, workingDirectory,
                    startupLocale, stdout, stderr);
        } catch (IllegalArgumentException | IOException configurationError) {
            renderer.render(CommandResult.failure("err.config", 2,
                            configurationError.getMessage()),
                    new RenderOptions("table", startupLocale.toLanguageTag(), false), err);
            return 2;
        } catch (Exception unexpected) {
            renderer.render(CommandResult.failure("err.internal", 1),
                    new RenderOptions("table", startupLocale.toLanguageTag(), false), err);
            return 1;
        } finally {
            out.flush();
            err.flush();
        }
    }
}
