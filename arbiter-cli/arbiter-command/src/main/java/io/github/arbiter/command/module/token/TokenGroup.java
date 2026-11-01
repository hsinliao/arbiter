package io.github.arbiter.command.module.token;

import io.github.arbiter.command.AbstractGroupCommand;
import picocli.CommandLine.Command;

@Command(name = "token", mixinStandardHelpOptions = true, resourceBundle = "messages")
public final class TokenGroup extends AbstractGroupCommand {
}
