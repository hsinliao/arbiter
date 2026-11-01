package io.github.arbiter.command;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class I18nBundleTest {
    @Test
    void baseAndChineseBundlesExposeTheSameKeys() {
        ResourceBundle base = ResourceBundle.getBundle("messages", Locale.ENGLISH);
        ResourceBundle chinese = ResourceBundle.getBundle(
                "messages", Locale.SIMPLIFIED_CHINESE);

        assertEquals(base.keySet(), chinese.keySet());
        assertFalse(base.getString("err.storage").isBlank());
        assertFalse(chinese.getString("err.storage").isBlank());
    }
}
