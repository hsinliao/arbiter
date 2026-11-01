package io.github.arbiter.command.i18n;

import io.github.arbiter.api.config.ArbiterKeys;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

public final class I18n {
    public static final String BASE_NAME = "messages";
    private static final Map<String, ResourceBundle> BUNDLES = new ConcurrentHashMap<>();

    private I18n() {
    }

    public static Locale resolveStartupLocale(Map<String, String> env, Locale systemDefault) {
        for (String name : new String[]{ArbiterKeys.Env.LANG, "LC_ALL", "LANG"}) {
            Locale parsed = parseLocale(env.get(name));
            if (parsed != null) {
                return parsed;
            }
        }
        return systemDefault;
    }

    public static Locale resolveResultLocale(String cliLanguage, Map<String, String> env,
                                             Locale systemDefault) {
        Locale fromOption = parseLocale(cliLanguage);
        return fromOption != null
                ? fromOption
                : resolveStartupLocale(env, systemDefault);
    }

    public static Locale parseLocale(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        Locale locale = Locale.forLanguageTag(tag.replace('_', '-'));
        if ("zh".equalsIgnoreCase(locale.getLanguage())) {
            return Locale.SIMPLIFIED_CHINESE;
        }
        return locale;
    }

    public static String get(Locale locale, String key, Object... args) {
        return lookup(locale, key, args);
    }

    private static String lookup(Locale locale, String key, Object... args) {
        String template = bundle(locale).getString(key);
        return new MessageFormat(template, locale).format(args == null ? new Object[0] : args);
    }

    public static ResourceBundle bundle(Locale locale) {
        Locale safeLocale = locale == null ? Locale.ENGLISH : locale;
        String cacheKey = safeLocale.toLanguageTag();
        return BUNDLES.computeIfAbsent(cacheKey, ignored ->
                ResourceBundle.getBundle(BASE_NAME, safeLocale,
                        Thread.currentThread().getContextClassLoader(),
                        ResourceBundle.Control.getNoFallbackControl(
                                ResourceBundle.Control.FORMAT_PROPERTIES)));
    }

}
