package io.github.arbiter.command.module.user;

import picocli.CommandLine.Command;

@Command(name = "user", mixinStandardHelpOptions = true, resourceBundle = "messages",
        subcommands = {})
public final class UserGroup {
}
