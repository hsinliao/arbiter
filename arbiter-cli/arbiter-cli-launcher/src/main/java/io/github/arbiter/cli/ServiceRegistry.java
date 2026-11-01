package io.github.arbiter.cli;

import io.github.arbiter.api.config.ArbiterKeys;

import io.github.arbiter.client.ApiAuthService;
import io.github.arbiter.client.ApiQueryService;
import io.github.arbiter.client.ApiSystemService;
import io.github.arbiter.client.ApiUserService;
import io.github.arbiter.client.ApiClient;
import io.github.arbiter.client.config.HttpTransportConfig;
import io.github.arbiter.client.config.TransportConfig;
import io.github.arbiter.client.config.UnixSocketTransportConfig;
import io.github.arbiter.client.transport.ApiTransport;
import io.github.arbiter.client.transport.HttpApiTransport;
import io.github.arbiter.client.transport.UnixSocketApiTransport;
import io.github.arbiter.client.ApiClient;
import io.github.arbiter.client.token.FileTokenStore;
import io.github.arbiter.command.RuntimeSignals;
import io.github.arbiter.command.i18n.I18n;
import io.github.arbiter.core.service.AuthService;
import io.github.arbiter.core.service.QueryService;
import io.github.arbiter.core.service.SystemService;
import io.github.arbiter.core.service.TokenService;
import io.github.arbiter.core.service.UserService;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Lazily creates the REST adapters after the command line has been parsed.
 * Configuration resolution therefore happens exactly once, immediately before
 * command execution, and bootstrap errors can use the same output contract as
 * ordinary command errors.
 */
final class ServiceRegistry {
    private final Map<String, String> environment;
    private final RuntimeSignals signals;
    private final AtomicReference<Consumer<String>> logSink;
    private final Path configDirectory;
    private final Supplier<String> environmentToken;

    private BootstrapSettings settings;
    private ApiClient apiClient;
    private FileTokenStore tokenStore;
    private AuthService authService;
    private SystemService systemService;
    private QueryService queryService;
    private UserService userService;

    ServiceRegistry(Map<String, String> environment, RuntimeSignals signals,
                    AtomicReference<Consumer<String>> logSink, Path configDirectory) {
        this.environment = environment;
        this.signals = signals;
        this.logSink = logSink;
        this.configDirectory = configDirectory;
        this.environmentToken = () -> environment.get(ArbiterKeys.Env.TOKEN);
    }

    void configure(BootstrapSettings settings) {
        if (this.settings == null) {
            this.settings = settings;
        } else if (!this.settings.equals(settings)) {
            throw new IllegalStateException("transport configuration was already resolved");
        }
    }

    public AuthService auth() {
        if (authService == null) {
            authService = new ApiAuthService(client(), tokenStore(), environmentToken);
        }
        return authService;
    }

    public TokenService token() {
        return (TokenService) auth();
    }

    public SystemService system() {
        if (systemService == null) {
            systemService = new ApiSystemService(client(), tokenStore());
        }
        return systemService;
    }

    public QueryService query() {
        if (queryService == null) {
            queryService = new ApiQueryService(client(), tokenStore());
        }
        return queryService;
    }

    public UserService user() {
        if (userService == null) {
            userService = new ApiUserService(client(), tokenStore());
        }
        return userService;
    }

    private ApiClient client() {
        if (apiClient == null) {
            if (settings == null) {
                throw new IllegalStateException("transport configuration has not been resolved");
            }
            TransportConfig transportConfig = settings.transportConfig();
            ApiTransport transport;
            if (transportConfig instanceof HttpTransportConfig http) {
                transport = new HttpApiTransport(http);
            } else if (transportConfig instanceof UnixSocketTransportConfig unix) {
                transport = new UnixSocketApiTransport(unix);
            } else {
                throw new IllegalStateException("Unsupported transport: " + transportConfig);
            }
            apiClient = new ApiClient(settings.apiClientConfig(), transport,
                    signals::verbose,
                    (key, args) -> I18n.get(signals.locale(), key, args),
                    message -> logSink.get().accept(message));
        }
        return apiClient;
    }

    private FileTokenStore tokenStore() {
        if (tokenStore == null) {
            tokenStore = new FileTokenStore(
                    configDirectory.resolve("credentials"), environmentToken);
        }
        return tokenStore;
    }
}
