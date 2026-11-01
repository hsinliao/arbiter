package io.github.arbiter.command;

import io.github.arbiter.core.service.QueryResult;
import io.github.arbiter.core.service.SystemStatus;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryCommandTest {
    @Test
    void rendersLocalizedResultInEnglishAndChinese() {
        StringWriter english = new StringWriter();
        var command = CommandTestSupport.commandLine(
                CommandTestSupport.nonInteractive(), CommandTestSupport.noSecret(),
                CommandTestSupport.noOpAuth(), () -> new SystemStatus("UP", "1", "now"),
                name -> new QueryResult(name, "value-42"), english, new StringWriter());

        int exit = command.execute("query", "--name", "alpha", "--lang", "en");

        assertEquals(0, exit);
        assertTrue(english.toString().contains("alpha"));
        assertTrue(english.toString().contains("value-42"));

        StringWriter chinese = new StringWriter();
        command = CommandTestSupport.commandLine(
                CommandTestSupport.nonInteractive(), CommandTestSupport.noSecret(),
                CommandTestSupport.noOpAuth(), () -> new SystemStatus("UP", "1", "now"),
                name -> new QueryResult(name, "value-42"), chinese, new StringWriter());
        exit = command.execute("query", "--name", "alpha", "--lang", "zh");

        assertEquals(0, exit);
        assertTrue(chinese.toString().contains("查询结果"));
    }

    @Test
    void emitsStableJsonFields() {
        StringWriter out = new StringWriter();
        var command = CommandTestSupport.commandLine(
                CommandTestSupport.nonInteractive(), CommandTestSupport.noSecret(),
                CommandTestSupport.noOpAuth(), () -> new SystemStatus("UP", "1", "now"),
                name -> new QueryResult(name, "value-42"), out, new StringWriter());

        int exit = command.execute("query", "--name", "alpha",
                "--output", "json", "--lang", "zh");

        assertEquals(0, exit);
        String json = out.toString();
        assertTrue(json.contains("\"success\":true"));
        assertTrue(json.contains("\"data\":{\"name\":\"alpha\",\"value\":\"value-42\"}"));
    }

    @Test
    void promptsInteractivelyAndRecordsPromptKey() {
        StubInputPrompter input = new StubInputPrompter(true, "resource.one");
        StringWriter out = new StringWriter();
        var command = CommandTestSupport.commandLine(
                input, CommandTestSupport.noSecret(), CommandTestSupport.noOpAuth(),
                () -> new SystemStatus("UP", "1", "now"),
                name -> new QueryResult(name, "ok"), out, new StringWriter());

        int exit = command.execute("query", "--lang", "en");

        assertEquals(0, exit);
        assertEquals(List.of("prompt.query.name"), input.promptKeys());
        assertTrue(out.toString().contains("resource.one"));
    }

    @Test
    void missingNameFailsImmediatelyWhenNonInteractive() {
        StringWriter err = new StringWriter();
        var command = CommandTestSupport.commandLine(
                CommandTestSupport.nonInteractive(), CommandTestSupport.noSecret(),
                CommandTestSupport.noOpAuth(), () -> new SystemStatus("UP", "1", "now"),
                name -> new QueryResult(name, "unused"), new StringWriter(), err);

        int exit = command.execute("query", "--lang", "en", "--no-input");

        assertEquals(2, exit);
        assertTrue(err.toString().contains("Missing required parameter"));
    }

    @Test
    void stopsAfterThreeInvalidInteractiveValues() {
        StubInputPrompter input = new StubInputPrompter(true,
                "bad name", "bad name", "bad name");
        StringWriter err = new StringWriter();
        var command = CommandTestSupport.commandLine(
                input, CommandTestSupport.noSecret(), CommandTestSupport.noOpAuth(),
                () -> new SystemStatus("UP", "1", "now"),
                name -> new QueryResult(name, "unused"), new StringWriter(), err);

        int exit = command.execute("query", "--lang", "en");

        assertEquals(2, exit);
        assertEquals(3, input.promptKeys().size());
        assertTrue(err.toString().contains("Too many invalid values"));
    }
}
