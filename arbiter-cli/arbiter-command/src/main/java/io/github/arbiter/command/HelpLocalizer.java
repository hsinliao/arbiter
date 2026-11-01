package io.github.arbiter.command;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Applies help.* resource keys to the already-built command model. Result
 * language changes never call this method again.
 */
public final class HelpLocalizer {
    private HelpLocalizer() {
    }

    public static void apply(CommandLine commandLine, Locale startupLocale) {
        ResourceBundle bundle = ResourceBundle.getBundle("messages", startupLocale);
        applyBundle(commandLine.getCommandSpec(), "root", bundle);
    }

    private static void applyBundle(CommandSpec spec, String path, ResourceBundle bundle) {
        spec.resourceBundle(bundle);
        setIfPresent(bundle, "help.usage.heading", spec.usageMessage()::synopsisHeading);
        setIfPresent(bundle, "help.commands.heading", spec.usageMessage()::commandListHeading);
        setIfPresent(bundle, "help.options.heading", spec.usageMessage()::optionListHeading);
        setIfPresent(bundle, "help.parameters.heading", spec.usageMessage()::parameterListHeading);
        String commandDescription = value(bundle, "help." + path + ".description");
        if (commandDescription != null) {
            spec.usageMessage().description(commandDescription);
        }
        for (var option : spec.options()) {
            option.messages(new CommandLine.Model.Messages(spec, bundle));
        }
        for (CommandLine child : spec.subcommands().values()) {
            String childPath = "root".equals(path)
                    ? child.getCommandName()
                    : path + "." + child.getCommandName();
            applyBundle(child.getCommandSpec(), childPath, bundle);
        }
    }

    private static String value(ResourceBundle bundle, String key) {
        try {
            return bundle.getString(key);
        } catch (java.util.MissingResourceException missing) {
            return null;
        }
    }

    private static void setIfPresent(ResourceBundle bundle, String key,
                                     java.util.function.Consumer<String> setter) {
        String value = value(bundle, key);
        if (value != null) {
            setter.accept(value);
        }
    }
}
