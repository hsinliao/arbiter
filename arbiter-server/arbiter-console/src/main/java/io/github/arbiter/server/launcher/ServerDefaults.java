package io.github.arbiter.server.launcher;

import java.util.LinkedHashMap;
import java.util.Map;

/** Default Spring Boot properties that are not application configuration. */
public final class ServerDefaults {
    private ServerDefaults() {
    }

    public static Map<String, Object> map() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("spring.application.name", "arbiter-server");
        defaults.put("spring.main.banner-mode", "off");
        defaults.put("management.endpoints.web.exposure.include",
                "health,info,metrics");
        defaults.put("spring.mvc.throw-exception-if-no-handler-found", "true");
        defaults.put("spring.lifecycle.timeout-per-shutdown-phase", "25s");
        return defaults;
    }
}
