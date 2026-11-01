package io.github.arbiter.server.launcher;

import io.github.arbiter.api.config.ArbiterHome;
import io.github.arbiter.api.config.ConfigKeys;
import io.github.arbiter.server.ArbiterServerApplication;
import io.github.arbiter.server.config.ServerTransport;
import io.github.arbiter.server.control.ServerPaths;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

/** Builds and starts the Spring Boot console runtime. */
public final class ServerLauncher {
    private final List<Consumer<SpringApplicationBuilder>> customizers =
            new ArrayList<>();

    public ServerLauncher withCustomizer(
            Consumer<SpringApplicationBuilder> customizer) {
        customizers.add(customizer);
        return this;
    }

    public ConfigurableApplicationContext start(String[] args) {
        // Single snapshot: the same Properties instance is both inspected for
        // the web type and installed as the Boot property source.
        Properties properties = loadProperties();

        SpringApplicationBuilder builder =
                new SpringApplicationBuilder(ArbiterServerApplication.class)
                        .web(webType(properties))
                        .properties(ServerDefaults.map())
                        .initializers(new ExternalPropertiesInitializer(properties));

        customizers.forEach(customizer -> customizer.accept(builder));
        return builder.run(args);
    }

    private static WebApplicationType webType(Properties properties) {
        ServerTransport.Mode mode = ServerTransport.parseMode(
                properties.getProperty(ConfigKeys.Common.TRANSPORT, "http"));
        return mode == ServerTransport.Mode.UNIX
                ? WebApplicationType.NONE
                : WebApplicationType.SERVLET;
    }

    private static Properties loadProperties() {
        Path home = ArbiterHome.root();
        if (home == null) {
            throw new IllegalStateException(
                    "arbiter.home is not set; start the server through bin/server-start");
        }
        try {
            return ServerPaths.loadProperties(ServerPaths.under(home));
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Unable to load server configuration: " + failure.getMessage(),
                    failure);
        }
    }
}
