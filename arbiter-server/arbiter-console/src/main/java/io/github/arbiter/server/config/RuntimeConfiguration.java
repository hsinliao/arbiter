package io.github.arbiter.server.config;

import io.github.arbiter.server.runtime.ServerRuntime;
import io.github.arbiter.server.uds.UdsRequestDispatcher;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Owns the server lifecycle bean (lock, PID, readiness, UDS). */
@Configuration(proxyBeanMethods = false)
public final class RuntimeConfiguration {
    @Bean(destroyMethod = "close")
    ServerRuntime serverRuntime(ServerConfig config,
                                UdsRequestDispatcher dispatcher) throws Exception {
        return new ServerRuntime(config, dispatcher);
    }
}
