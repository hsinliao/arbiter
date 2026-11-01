package io.github.arbiter.api;

/**
 * Paths below the configured API prefix. Both the CLI client and the
 * reference server use these constants, so a route rename cannot silently
 * desynchronize the two modules.
 */
public final class ApiRoutes {
    public static final String LOGIN = "/auth/login";
    public static final String WHO_AM_I = "/auth/whoami";
    public static final String STATUS = "/status";
    public static final String QUERY = "/query";
    public static final String USERS = "/users";

    private ApiRoutes() {
    }
}
