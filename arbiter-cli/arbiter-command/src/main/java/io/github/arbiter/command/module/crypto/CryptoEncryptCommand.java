package io.github.arbiter.command.module.crypto;

import picocli.CommandLine.Model.CommandSpec;

import io.github.arbiter.command.AbstractCommand;
import io.github.arbiter.command.result.CommandResult;
import io.github.arbiter.command.result.payload.EncryptedPayload;
import io.github.arbiter.api.crypto.CryptoException;
import io.github.arbiter.api.crypto.CryptoKeys;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.util.concurrent.Callable;

@Command(name = "encrypt", mixinStandardHelpOptions = true, resourceBundle = "messages")
public final class CryptoEncryptCommand extends AbstractCommand implements Callable<Integer> {
    @Spec
    private CommandSpec spec;

    @Parameters(index = "0", arity = "1", paramLabel = "TEXT")
    private String text;

    @Override
    public Integer call() {
        try {
            String ciphertext = CryptoKeys.encrypt(text);
            CommandResult result = CommandResult.success("cmd.crypto.encrypt.success",
                    new EncryptedPayload(ciphertext), ciphertext);
            render(spec, result);
            return result.exitCode();
        } catch (CryptoException failure) {
            throw CryptoErrors.map(failure);
        } catch (IOException unreadable) {
            throw CryptoErrors.unreadable();
        }
    }
}
