package io.github.arbiter.agent.spi.model;

import io.github.arbiter.agent.spi.AgentSpiVersion;

import java.util.Map;

public record PluginConfig(String pluginCode, int spiVersion, Map<String, Object> props) {
    public PluginConfig {
        props = props == null ? Map.of() : Map.copyOf(props);
    }

    public PluginConfig(String pluginCode, Map<String, Object> props) {
        this(pluginCode, AgentSpiVersion.MAJOR, props);
    }

    public static PluginConfig of(String pluginCode) {
        return new PluginConfig(pluginCode, AgentSpiVersion.MAJOR, Map.of());
    }

    public <T> T get(String key, Class<T> clazz) {
        Object value = props.get(key);
        return value == null ? null : clazz.cast(value);
    }

    public Object get(String key) {
        return props.get(key);
    }

    public Map<String, Object> getProps() {
        return props;
    }

    public String getPluginCode() {
        return pluginCode;
    }
}
