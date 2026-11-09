package io.github.arbiter.agent.spi.model;

import io.github.arbiter.agent.spi.AgentSpiVersion;

public record PluginDescriptor(
        String pluginType,
        String code,
        String name,
        String version,
        String description,
        int spiVersion,
        boolean dynamicLoadable,
        String configSchema,
        ClassLoader pluginClassLoader
) {
    public PluginDescriptor {
        if (pluginType == null || pluginType.isBlank()) {
            throw new IllegalArgumentException("Plugin type is required");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Plugin code is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Plugin name is required");
        }
        version = version == null || version.isBlank() ? "0.0.0" : version;
        description = description == null ? "" : description;
        spiVersion = spiVersion <= 0 ? AgentSpiVersion.MAJOR : spiVersion;
        configSchema = configSchema == null ? "" : configSchema;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String pluginType;
        private String code;
        private String name;
        private String version = "0.0.0";
        private String description = "";
        private int spiVersion = AgentSpiVersion.MAJOR;
        private boolean dynamicLoadable;
        private String configSchema = "";
        private ClassLoader pluginClassLoader;

        public Builder pluginType(String pluginType) { this.pluginType = pluginType; return this; }
        public Builder code(String code) { this.code = code; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder spiVersion(int spiVersion) { this.spiVersion = spiVersion; return this; }
        public Builder dynamicLoadable(boolean dynamicLoadable) {
            this.dynamicLoadable = dynamicLoadable;
            return this;
        }
        public Builder configSchema(String configSchema) { this.configSchema = configSchema; return this; }
        public Builder pluginClassLoader(ClassLoader pluginClassLoader) {
            this.pluginClassLoader = pluginClassLoader;
            return this;
        }

        public PluginDescriptor build() {
            return new PluginDescriptor(
                    pluginType, code, name, version, description, spiVersion,
                    dynamicLoadable, configSchema, pluginClassLoader);
        }
    }
}
