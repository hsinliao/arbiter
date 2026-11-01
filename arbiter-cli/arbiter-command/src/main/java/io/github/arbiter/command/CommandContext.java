package io.github.arbiter.command;

import io.github.arbiter.command.result.ResultRenderer;
import io.github.arbiter.core.input.InputPrompter;

import java.util.function.Function;

public record CommandContext(InputPrompter input,
                             ResultRenderer renderer,
                             RuntimeSignals signals,
                             Function<String, String> environment,
                             String fixedResultLanguage) {
}
