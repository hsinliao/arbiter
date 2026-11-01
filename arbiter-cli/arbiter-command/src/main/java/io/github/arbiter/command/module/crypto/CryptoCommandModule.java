package io.github.arbiter.command.module.crypto;

import io.github.arbiter.command.CommandModule;
import picocli.CommandLine;

public final class CryptoCommandModule implements CommandModule {
    @Override
    public int order() {
        return 30;
    }

    @Override
    public String name() {
        return "crypto";
    }

    @Override
    public CommandLine commandLine() {
        CommandLine crypto = new CommandLine(new CryptoGroup());
        crypto.addSubcommand("encrypt", new CommandLine(new CryptoEncryptCommand()));
        crypto.addSubcommand("decrypt", new CommandLine(new CryptoDecryptCommand()));
        return crypto;
    }
}
