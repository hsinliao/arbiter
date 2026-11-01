package io.github.arbiter.command.module.server;

import io.github.arbiter.command.AbstractGroupCommand;
import picocli.CommandLine.Command;

@Command(name = "server", mixinStandardHelpOptions = true, resourceBundle = "messages")
public final class ServerGroup extends AbstractGroupCommand {
}
