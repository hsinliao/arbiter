package io.github.arbiter.command.module.crypto;

import io.github.arbiter.command.AbstractGroupCommand;
import picocli.CommandLine.Command;

@Command(name = "crypto", mixinStandardHelpOptions = true, resourceBundle = "messages")
public final class CryptoGroup extends AbstractGroupCommand {
}
