package io.github.arbiter.agent.core;

import io.github.arbiter.agent.spi.model.PluginDescriptor;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class PluginRegistry {

    private final Map<String, PluginDescriptor> metaIndex = new ConcurrentHashMap<>();

    public void register(PluginDescriptor meta) {
        if (meta == null || meta.code() == null || meta.code().isBlank()) {
            throw new IllegalArgumentException("Plugin metadata and code are required");
        }
        PluginDescriptor previous = metaIndex.putIfAbsent(meta.code(), meta);
        if (previous != null && previous != meta) {
            throw new IllegalStateException("Plugin already registered: " + meta.code());
        }
    }

    public void replace(PluginDescriptor meta) {
        if (meta == null || meta.code() == null || meta.code().isBlank()) {
            throw new IllegalArgumentException("Plugin metadata and code are required");
        }
        metaIndex.put(meta.code(), meta);
    }

    public Optional<PluginDescriptor> find(String code) {
        return Optional.ofNullable(metaIndex.get(code));
    }

    public boolean remove(String code) {
        return metaIndex.remove(code) != null;
    }

    public Collection<PluginDescriptor> all() {
        return List.copyOf(metaIndex.values());
    }

    public List<PluginDescriptor> byType(String pluginType) {
        return metaIndex.values().stream()
                .filter(meta -> Objects.equals(meta.pluginType(), pluginType))
                .toList();
    }
}
