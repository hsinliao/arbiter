package io.github.arbiter.cli;

import io.github.arbiter.api.config.ArbiterKeys;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CLIMainTest {
    @TempDir
    Path temporaryDirectory;

    private String originalHome;

    @BeforeEach
    void isolateCredentials() {
        originalHome = System.getProperty("user.home");
        System.setProperty(ArbiterKeys.SystemProperty.USER_HOME, temporaryDirectory.toString());
    }

    @AfterEach
    void restoreHome() {
        System.setProperty("user.home", originalHome);
        System.clearProperty(ArbiterKeys.SystemProperty.USER_HOME);
    }

    @Test
    void missingTokenFailsWithAuthenticationExitCode() {
        Result result = run(new String[]{"status", "--no-input", "--lang", "en"});

        assertEquals(3, result.code());
        assertTrue(result.err().contains("Authentication failed"));
    }

    @Test
    void structuredErrorIsWrittenToStdoutWithoutDiagnostics() {
        Result result = run(new String[]{"--output", "json", "--lang", "en",
                "status", "--no-input"});

        assertEquals(3, result.code());
        assertTrue(result.out().contains("\"success\":false"));
        assertTrue(result.out().contains("\"exitCode\":3"));
        assertEquals("", result.err());
    }

    @Test
    void missingInteractiveParameterDoesNotBlockAndReturnsTwo() {
        Result result = run(new String[]{"query", "--no-input", "--lang", "en"});

        assertEquals(2, result.code());
        assertTrue(result.err().contains("Missing required parameter"));
    }

    @Test
    void corruptCredentialsUseTheLocalStorageError() throws IOException {
        Path configDirectory = Files.createDirectories(
                temporaryDirectory.resolve(".config/arbiter"));
        Files.writeString(configDirectory.resolve("credentials"), "{not-json");

        Result result = run(new String[]{"status", "--no-input", "--lang", "en"});

        assertEquals(2, result.code());
        assertTrue(result.err().contains("local credentials"), result.err());
    }

    @Test
    void serverOptionIsNoLongerAccepted() {
        Result result = run(new String[]{"--server", "http://localhost:1", "status"});

        assertEquals(2, result.code());
    }

    @Test
    void serverCommandsDoNotResolveRemoteConfiguration() {
        Result result = run(new String[]{"server", "status", "--lang", "en"});

        assertEquals(0, result.code(), result.err());
        assertTrue(result.out().contains("stopped"), result.out());
    }

    @Test
    void langChangesResultsButNotStartupHelpLanguage() {
        Result result = run(new String[]{"--lang", "zh-CN", "--help"});

        assertEquals(0, result.code());
        assertTrue(result.out().contains("Usage:"));
        assertFalse(result.out().contains("用法："));
    }

    @Test
    void startupLanguageEnvironmentLocalizesHelp() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int code = CLIMain.run(new String[]{"--help"}, Map.of("LANG", "zh_CN"),
                temporaryDirectory, out, err);

        assertEquals(0, code);
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("远程服务命令行客户端"));
    }

    @Test
    void passwordIsNotAcceptedAsACommandLineOption() {
        Result result = run(new String[]{"--lang", "en",
                "auth", "login", "--username", "alice", "--password", "D0NotLeak123"});

        assertEquals(2, result.code());
        assertFalse(result.err().contains("D0NotLeak123"), result.err());
    }

    private Result run(String[] args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = CLIMain.run(args, Map.of("LANG", "en"), temporaryDirectory, out, err);
        return new Result(code, out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8));
    }

    private record Result(int code, String out, String err) {
    }
}
