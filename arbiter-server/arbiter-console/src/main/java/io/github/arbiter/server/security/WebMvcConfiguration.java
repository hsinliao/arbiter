package io.github.arbiter.server.security;

import io.github.arbiter.server.config.ServerConfig;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.bind.annotation.RestController;

@Configuration(proxyBeanMethods = false)
public final class WebMvcConfiguration implements WebMvcConfigurer {
    private final ServerConfig config;
    private final TokenAuthenticationInterceptor tokenInterceptor;

    public WebMvcConfiguration(ServerConfig config,
                               TokenAuthenticationInterceptor tokenInterceptor) {
        this.config = config;
        this.tokenInterceptor = tokenInterceptor;
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        String prefix = config.apiPrefix();
        if (!"/".equals(prefix)) {
            configurer.addPathPrefix(prefix,
                    HandlerTypePredicate.forAnnotation(RestController.class));
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        String prefix = config.apiPrefix();
        String pattern = "/".equals(prefix) ? "/**" : prefix + "/**";
        registry.addInterceptor(tokenInterceptor).addPathPatterns(pattern);
    }
}
