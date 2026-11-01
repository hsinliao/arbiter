package io.github.arbiter.server;

import io.github.arbiter.api.config.ArbiterHome;
import io.github.arbiter.api.config.ConfigKeys;
import io.github.arbiter.server.control.ServerControl;
import io.github.arbiter.server.control.ServerPaths;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.core.env.PropertiesPropertySource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

@SpringBootApplication
public final class ArbiterServerApplication {
    static {
        System.setProperty("PID", Long.toString(ProcessHandle.current().pid()));
    }

    private ArbiterServerApplication() {
    }

    public static void main(String[] args) {
        if (ServerControl.isControlCommand(args)) {
            System.err.println("arbiter-server: control flags are handled by"
                    + " bin/server-start; use --check, --stop, --help, or --version"
                    + " through the launcher");
            System.exit(2);
            return;
        }
        Properties properties = loadProperties();
        String transport = properties.getProperty(ConfigKeys.Common.TRANSPORT, "http");
        SpringApplication application = new SpringApplication(
                ArbiterServerApplication.class);
        if ("unix".equalsIgnoreCase(transport.trim())) {
            application.setWebApplicationType(WebApplicationType.NONE);
        }
        java.util.Map<String, Object> defaults = new java.util.LinkedHashMap<>();
        defaults.put("spring.application.name", "arbiter-server");
        defaults.put("spring.main.banner-mode", "off");
        defaults.put("management.endpoints.web.exposure.include", "health,info,metrics");
        defaults.put("spring.web.resources.add-mappings", "false");
        defaults.put("spring.mvc.throw-exception-if-no-handler-found", "true");
        defaults.put("spring.lifecycle.timeout-per-shutdown-phase", "25s");
        application.setDefaultProperties(defaults);
        application.addInitializers(context -> context.getEnvironment()
                .getPropertySources()
                .addFirst(new PropertiesPropertySource("arbiterFiles", properties)));
        application.run(args);
    }

    static Properties loadProperties() {
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
