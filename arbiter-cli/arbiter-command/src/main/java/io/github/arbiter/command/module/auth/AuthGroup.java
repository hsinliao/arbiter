package io.github.arbiter.command.module.auth;

import io.github.arbiter.command.AbstractGroupCommand;
import picocli.CommandLine.Command;

@Command(name = "auth", mixinStandardHelpOptions = true, resourceBundle = "messages")
public final class AuthGroup extends AbstractGroupCommand {
}
