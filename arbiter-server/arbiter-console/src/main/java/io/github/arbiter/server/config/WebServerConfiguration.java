package io.github.arbiter.server.config;

import io.github.arbiter.server.tls.SslBundleFactory;

import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.web.server.Shutdown;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.InetAddress;

/** Address, port, graceful shutdown, and TLS material for the embedded server. */
@Configuration(proxyBeanMethods = false)
public final class WebServerConfiguration {
    @Bean
    WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> webServerCustomizer(
            ServerConfig config) {
        return factory -> {
            try {
                factory.setAddress(InetAddress.getByName(config.host()));
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "Invalid server host: " + config.host(), failure);
            }
            factory.setPort(config.port());
            factory.setShutdown(Shutdown.GRACEFUL);
            if (config.tls().enabled()) {
                try {
                    SslBundles bundles = SslBundleFactory.create(config.tls());
                    factory.setSslBundles(bundles);
                    Ssl ssl = Ssl.forBundle(SslBundleFactory.BUNDLE_NAME);
                    if (config.tls().clientAuth()) {
                        ssl.setClientAuth(Ssl.ClientAuth.NEED);
                    }
                    factory.setSsl(ssl);
                } catch (Exception failure) {
                    throw new IllegalStateException(
                            "Unable to initialize TLS material", failure);
                }
            }
        };
    }
}
