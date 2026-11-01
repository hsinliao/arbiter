package io.github.arbiter.server.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Binds external properties and produces the immutable runtime configuration. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ServerProperties.class)
public final class ServerConfigConfiguration {
    @Bean
    ServerConfig serverConfig(ServerProperties properties) {
        return properties.toServerConfig();
    }
}
