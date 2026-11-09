package io.github.arbiter.agent.spi.annotation;

import io.github.arbiter.agent.spi.AgentSpiVersion;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AgentPlugin {

    String type();

    String code();

    String name();

    String description() default "";

    String version() default "0.0.0";

    int spiVersion() default AgentSpiVersion.MAJOR;

    boolean dynamicLoadable() default false;
}
