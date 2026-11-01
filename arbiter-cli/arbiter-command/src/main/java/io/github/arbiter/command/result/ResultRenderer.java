package io.github.arbiter.command.result;

import io.github.arbiter.api.config.ArbiterKeys;

import io.github.arbiter.core.exception.DomainException;
import io.github.arbiter.command.i18n.I18n;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.function.Function;

public final class ResultRenderer {
    private final Function<String, String> environment;
    private final Locale systemDefault;

    public ResultRenderer(Function<String, String> environment, Locale systemDefault) {
        this.environment = Objects.requireNonNull(environment);
        this.systemDefault = Objects.requireNonNull(systemDefault);
    }

    public void render(CommandResult result, RenderOptions options, PrintWriter out) {
        Locale locale = resolveLocale(options);
        if (options.json()) {
            renderJson(result, locale, options, out);
        } else {
            renderText(result, locale, options, out);
        }
    }

    public void renderError(DomainException error, RenderOptions options, PrintWriter out) {
        Map<String, Object> data = options.verbose() ? serverData(error) : Map.of();
        CommandResult result = new CommandResult(false, error.messageKey(), error.args(),
                data, error.exitCode());
        render(result, options, out);
    }

    public String translate(String key, Locale locale, Object... args) {
        try {
            return I18n.get(locale, key, args);
        } catch (MissingResourceException missing) {
            return key;
        }
    }

    public Locale resolveLocale(RenderOptions options) {
        Map<String, String> environmentValues = new HashMap<>();
        putIfPresent(environmentValues, ArbiterKeys.Env.LANG, env(ArbiterKeys.Env.LANG));
        putIfPresent(environmentValues, "LC_ALL", env("LC_ALL"));
        putIfPresent(environmentValues, "LANG", env("LANG"));
        return I18n.resolveResultLocale(options.language(), environmentValues, systemDefault);
    }

    private void renderText(CommandResult result, Locale locale, RenderOptions options,
                            PrintWriter out) {
        String message = localizeTracked(result.messageKey(), locale, result.args(), out,
                options.verbose());
        out.println(message);
        if (options.verbose()) {
            appendServerError(result.data(), locale, out);
        }
    }

    private void renderJson(CommandResult result, Locale locale, RenderOptions options,
                            PrintWriter out) {
        StringWriter warningSink = new StringWriter();
        PrintWriter warningWriter = new PrintWriter(warningSink);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("success", result.success());
        root.put("exitCode", result.exitCode());
        root.put("messageKey", result.messageKey());
        root.put("message", localizeTracked(result.messageKey(), locale, result.args(),
                warningWriter,
                options.verbose()));
        root.put("data", result.data());
        root.put("warnings", warningSink.toString().lines().toList());
        if (options.verbose()) {
            Object code = result.data().get("serverCode");
            Object serverMessage = result.data().get("serverMessage");
            if (code != null || serverMessage != null) {
                Map<String, Object> server = new LinkedHashMap<>();
                server.put("code", Objects.toString(code, ""));
                server.put("message", Objects.toString(serverMessage, ""));
                root.put("server", server);
            }
        }
        out.println(JsonSupport.write(root));
    }

    private String localizeTracked(String key, Locale locale, Object[] args, PrintWriter out,
                                   boolean verbose) {
        Object[] localizedArgs = localizeArguments(args, locale, out, verbose);
        try {
            return I18n.get(locale, key, localizedArgs);
        } catch (MissingResourceException missing) {
            if (verbose) {
                out.println(translate("warn.missing.message", locale, key));
            }
            try {
                return I18n.get(Locale.ENGLISH, key, localizedArgs);
            } catch (MissingResourceException alsoMissing) {
                return key;
            }
        }
    }

    private Object[] localizeArguments(Object[] args, Locale locale, PrintWriter out,
                                       boolean verbose) {
        Object[] localized = new Object[args.length];
        for (int index = 0; index < args.length; index++) {
            Object argument = args[index];
            if (argument instanceof LocalizedArgument nested) {
                localized[index] = localizeTracked(nested.messageKey(), locale, nested.args(),
                        out, verbose);
            } else {
                localized[index] = argument;
            }
        }
        return localized;
    }

    private void appendServerError(Map<String, Object> data, Locale locale, PrintWriter out) {
        Object code = data.get("serverCode");
        Object message = data.get("serverMessage");
        if (code == null && message == null) {
            return;
        }
        out.println(translate("verbose.server.message", locale,
                Objects.toString(code, ""), Objects.toString(message, "")));
    }

    private static Map<String, Object> serverData(DomainException error) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("serverCode", Objects.requireNonNullElse(error.serverCode(), ""));
        data.put("serverMessage", Objects.requireNonNullElse(error.serverMessage(), ""));
        return data;
    }

    private String env(String name) {
        return environment.apply(name);
    }

    private static void putIfPresent(Map<String, String> values, String key, String value) {
        if (value != null) {
            values.put(key, value);
        }
    }
}
