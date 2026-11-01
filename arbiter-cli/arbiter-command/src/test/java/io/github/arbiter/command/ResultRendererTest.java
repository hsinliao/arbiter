package io.github.arbiter.command;

import io.github.arbiter.core.exception.ServerException;
import io.github.arbiter.command.result.CommandResult;
import io.github.arbiter.command.result.RenderOptions;
import io.github.arbiter.command.result.ResultRenderer;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultRendererTest {
    @Test
    void usesEnglishFallbackAndVerboseWarningForMissingTranslation() {
        StringWriter output = new StringWriter();
        ResultRenderer renderer = new ResultRenderer(Map.<String, String>of()::get,
                Locale.ENGLISH);
        renderer.render(new CommandResult(true, "sample.missing", new Object[0],
                        Map.of(), 0),
                new RenderOptions("table", "zh-CN", true), new PrintWriter(output, true));

        assertTrue(output.toString().contains("sample.missing"), output.toString());
        assertTrue(output.toString().lines().count() >= 2, output.toString());
    }

    @Test
    void translatesNestedLocalizedArguments() {
        StringWriter output = new StringWriter();
        ResultRenderer renderer = new ResultRenderer(Map.<String, String>of()::get,
                Locale.ENGLISH);
        renderer.render(CommandResult.success("cmd.auth.logout.success"),
                new RenderOptions("table", "zh-CN", false), new PrintWriter(output, true));

        assertTrue(output.toString().contains("本地凭证"), output.toString());
    }

    @Test
    void serverDiagnosticsAreOnlyEmittedInVerboseErrors() {
        ResultRenderer renderer = new ResultRenderer(Map.<String, String>of()::get,
                Locale.ENGLISH);
        ServerException error = new ServerException("INTERNAL", "sensitive-detail");

        StringWriter quiet = new StringWriter();
        renderer.renderError(error, new RenderOptions("json", "en", false),
                new PrintWriter(quiet, true));
        assertFalse(quiet.toString().contains("sensitive-detail"), quiet.toString());
        assertFalse(quiet.toString().contains("serverMessage"), quiet.toString());

        StringWriter verbose = new StringWriter();
        renderer.renderError(error, new RenderOptions("json", "en", true),
                new PrintWriter(verbose, true));
        assertTrue(verbose.toString().contains("sensitive-detail"), verbose.toString());
    }

    @Test
    void missingTranslationWarningIsPartOfTheJsonDocument() {
        StringWriter output = new StringWriter();
        ResultRenderer renderer = new ResultRenderer(Map.<String, String>of()::get,
                Locale.ENGLISH);

        renderer.render(CommandResult.failure("sample.missing", 1),
                new RenderOptions("json", "zh-CN", true),
                new PrintWriter(output, true));

        String json = output.toString();
        assertEquals(1, json.lines().filter(line -> !line.isBlank()).count());
        assertTrue(json.startsWith("{"));
        assertTrue(json.contains("\"warnings\":[\""));
        assertTrue(json.contains("sample.missing"));
    }
}
