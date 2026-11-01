package io.github.arbiter.command;

import io.github.arbiter.core.exception.UsageException;
import io.github.arbiter.command.result.CommandResult;
import picocli.CommandLine;

import java.util.Locale;
import java.util.regex.Pattern;

public abstract class AbstractCommand {
    static final int INTERACTIVE_RETRY_LIMIT = InteractionPolicy.RETRY_LIMIT;
    private static final Pattern OBJECT_NAME = Pattern.compile("[A-Za-z0-9._-]{1,100}");

    protected final InteractionPolicy interaction(CommandLine.Model.CommandSpec spec) {
        return new InteractionPolicy(CliRoot.from(spec), spec.commandLine().getErr());
    }

    protected final void render(CommandLine.Model.CommandSpec spec, CommandResult result) {
        CliRoot root = CliRoot.from(spec);
        root.renderer().render(result, root.renderOptions(), spec.commandLine().getOut());
    }

    protected final String resolveObjectName(CommandLine.Model.CommandSpec spec,
                                             String supplied,
                                             String promptKey,
                                             String fieldLabelKey) {
        return interaction(spec).requiredText(supplied, promptKey, fieldLabelKey,
                OBJECT_NAME, "err.usage.invalidName");
    }

    protected final void validateObjectName(CommandLine.Model.CommandSpec spec, String value) {
        if (value == null || !OBJECT_NAME.matcher(value).matches()) {
            CliRoot root = CliRoot.from(spec);
            throw new UsageException("err.usage.invalidName", value == null ? "" : value);
        }
    }

    protected final void validateRequiredText(CommandLine.Model.CommandSpec spec, String value,
                                              String fieldLabelKey) {
        if (value == null || value.isBlank()) {
            CliRoot root = CliRoot.from(spec);
            throw new UsageException("err.usage.missing",
                    root.renderer().translate(fieldLabelKey, resultLocale(root)));
        }
    }

    protected final Locale resultLocale(CliRoot root) {
        return root.renderer().resolveLocale(root.renderOptions());
    }
}
