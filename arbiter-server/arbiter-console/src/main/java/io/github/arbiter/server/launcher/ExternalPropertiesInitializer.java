package io.github.arbiter.server.launcher;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.PropertiesPropertySource;

import java.util.Properties;

/** Adds the external {@code conf/*.properties} source ahead of Boot's sources. */
public final class ExternalPropertiesInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private final Properties properties;

    public ExternalPropertiesInitializer(Properties properties) {
        this.properties = properties;
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        context.getEnvironment().getPropertySources()
                .addFirst(new PropertiesPropertySource("arbiter", properties));
    }
}
