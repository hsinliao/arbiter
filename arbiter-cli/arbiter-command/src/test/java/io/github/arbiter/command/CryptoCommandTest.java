package io.github.arbiter.command;

import io.github.arbiter.api.config.ArbiterKeys;
import io.github.arbiter.api.crypto.CryptoKeys;
import io.github.arbiter.command.module.crypto.CryptoCommandModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CryptoCommandTest {
    private static final byte[] OTHER_KEY = new byte[32];

    static {
        for (int index = 0; index < OTHER_KEY.length; index++) {
            OTHER_KEY[index] = (byte) (index + 1);
        }
    }

    @TempDir
    Path home;

    private String previousHome;

    @BeforeEach
    void useTemporaryHome() {
        previousHome = System.getProperty(ArbiterKeys.SystemProperty.HOME);
        System.setProperty(ArbiterKeys.SystemProperty.HOME, home.toString());
    }

    @AfterEach
    void restoreHome() {
        if (previousHome == null) {
            System.clearProperty(ArbiterKeys.SystemProperty.HOME);
        } else {
            System.setProperty(ArbiterKeys.SystemProperty.HOME, previousHome);
        }
    }

    private static picocli.CommandLine commandLine(StringWriter out, StringWriter err) {
        return CommandTestSupport.commandLine(CommandTestSupport.nonInteractive(),
                CommandTestSupport.noSecret(), List.of(new CryptoCommandModule()),
                name -> null, out, err);
    }

    @Test
    void encryptsAndDecryptsWithTheServerGeneratedKey() throws Exception {
        assertTrue(CryptoKeys.ensure());

        StringWriter encryptedOut = new StringWriter();
        StringWriter encryptedErr = new StringWriter();
        int encryptedExit = commandLine(encryptedOut, encryptedErr).execute(
                "crypto", "encrypt", "hello 中文");
        assertEquals(0, encryptedExit, encryptedErr.toString());
        String ciphertext = encryptedOut.toString()
                .replace("Ciphertext: ", "").trim();
        assertTrue(ciphertext.startsWith("arbiter:v1:aes-256-gcm:k1:"),
                ciphertext);

        StringWriter decryptedOut = new StringWriter();
        StringWriter decryptedErr = new StringWriter();
        int decryptedExit = commandLine(decryptedOut, decryptedErr).execute(
                "crypto", "decrypt", ciphertext);
        assertEquals(0, decryptedExit, decryptedErr.toString());
        assertTrue(decryptedOut.toString().contains("hello 中文"));
    }

    @Test
    void keyMustBeInitializedByTheServer() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exit = commandLine(out, err).execute("crypto", "encrypt", "text");

        assertEquals(2, exit, err.toString());
        assertTrue(err.toString().contains("not initialized"), err.toString());
    }

    @Test
    void rejectsAnInvalidKeyFile() throws Exception {
        Path keyFile = CryptoKeys.path();
        Files.createDirectories(keyFile.getParent());
        Files.writeString(keyFile, "not-a-key", StandardCharsets.UTF_8);

        StringWriter err = new StringWriter();
        int exit = commandLine(new StringWriter(), err).execute(
                "crypto", "encrypt", "text");

        assertEquals(2, exit, err.toString());
        assertTrue(err.toString().contains("key ring is invalid"), err.toString());
    }

    @Test
    void wrongKeyFailsDecryption() throws Exception {
        assertTrue(CryptoKeys.ensure());
        StringWriter encryptedOut = new StringWriter();
        commandLine(encryptedOut, new StringWriter()).execute(
                "crypto", "encrypt", "secret");
        String ciphertext = encryptedOut.toString()
                .replace("Ciphertext: ", "").trim();

        Files.writeString(CryptoKeys.path(),
                "version=1\nactive=k1\nkey.k1="
                        + Base64.getEncoder().encodeToString(OTHER_KEY) + "\n",
                StandardCharsets.UTF_8);

        StringWriter err = new StringWriter();
        int exit = commandLine(new StringWriter(), err).execute(
                "crypto", "decrypt", ciphertext);

        assertEquals(1, exit, err.toString());
        assertTrue(err.toString().contains("Unable to decrypt"), err.toString());
    }
}
