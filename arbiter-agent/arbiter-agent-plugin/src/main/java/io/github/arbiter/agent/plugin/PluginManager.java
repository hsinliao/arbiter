package io.github.arbiter.agent.plugin;

import io.github.arbiter.agent.core.PluginRegistry;
import io.github.arbiter.agent.spi.AgentSpiVersion;
import io.github.arbiter.agent.spi.annotation.AgentPlugin;
import io.github.arbiter.agent.spi.lifecycle.PluginLifecycle;
import io.github.arbiter.agent.spi.model.PluginConfig;
import io.github.arbiter.agent.spi.model.PluginDescriptor;
import io.github.arbiter.agent.spi.plugin.AgentPluginProvider;

import java.io.Closeable;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PluginManager implements AutoCloseable {

    private static final System.Logger LOGGER = System.getLogger(PluginManager.class.getName());

    private final PluginRegistry registry;
    private final PluginSandbox sandbox;
    private final Map<String, PluginHandle> handles = new ConcurrentHashMap<>();
    private final Map<String, Closeable> closeables = new ConcurrentHashMap<>();
    private final Map<Closeable, Integer> closeableReferences = new IdentityHashMap<>();

    public PluginManager(PluginRegistry registry) {
        this(registry, new ClassLoaderPluginSandbox());
    }

    public PluginManager(PluginRegistry registry, PluginSandbox sandbox) {
        this.registry = registry == null ? new PluginRegistry() : registry;
        this.sandbox = sandbox == null ? new ClassLoaderPluginSandbox() : sandbox;
    }

    public synchronized PluginHandle registerAnnotated(Object plugin) {
        Objects.requireNonNull(plugin, "plugin");
        AgentPlugin annotation = plugin.getClass().getAnnotation(AgentPlugin.class);
        if (annotation == null) {
            throw new IllegalArgumentException("Plugin class is missing @AgentPlugin: " + plugin.getClass().getName());
        }
        PluginDescriptor meta = PluginDescriptor.builder()
                .pluginType(annotation.type())
                .code(annotation.code())
                .name(annotation.name())
                .version(annotation.version())
                .spiVersion(annotation.spiVersion())
                .description(annotation.description())
                .dynamicLoadable(annotation.dynamicLoadable())
                .pluginClassLoader(plugin.getClass().getClassLoader())
                .build();
        return register(meta, plugin, null);
    }

    public synchronized PluginHandle register(PluginDescriptor meta, Object instance, Closeable closeable) {
        Objects.requireNonNull(meta, "meta");
        Objects.requireNonNull(instance, "instance");
        String code = requirePluginCode(meta.code());
        if (meta.spiVersion() != AgentSpiVersion.MAJOR) {
            throw new IllegalArgumentException(
                    "Unsupported plugin SPI version: " + meta.spiVersion());
        }
        if (handles.containsKey(code)) {
            throw new IllegalStateException("Plugin already registered: " + code);
        }
        registry.register(meta);
        PluginHandle handle = new PluginHandle(meta, instance, PluginState.DISCOVERED, "");
        handles.put(code, handle);
        if (closeable != null) {
            closeables.put(code, closeable);
            closeableReferences.merge(closeable, 1, Integer::sum);
        }
        return handle;
    }

    public synchronized PluginHandle initialize(String code, PluginConfig config) {
        PluginHandle handle = requireHandle(code);
        if (handle.state() == PluginState.INITIALIZED) {
            return handle;
        }
        if (handle.state() == PluginState.DESTROYED) {
            throw new IllegalStateException("Cannot initialize destroyed plugin: " + code);
        }
        try {
            if (!(handle.instance() instanceof PluginLifecycle lifecycle)) {
                throw new IllegalArgumentException("Plugin does not implement PluginLifecycle: " + code);
            }
            PluginConfig effectiveConfig = config == null
                    ? PluginConfig.of(code)
                    : new PluginConfig(code, AgentSpiVersion.MAJOR, config.getProps());
            lifecycle.start(effectiveConfig);
            return replaceState(handle, PluginState.INITIALIZED, "");
        } catch (RuntimeException error) {
            replaceState(handle, PluginState.FAILED, error.getMessage());
            throw error;
        }
    }

    public synchronized int initializeAll(PluginConfig config) {
        int initialized = 0;
        for (String code : handles.keySet()) {
            if (handles.get(code).state() != PluginState.INITIALIZED) {
                initialize(code, config);
                initialized++;
            }
        }
        return initialized;
    }

    public synchronized void destroy(String code) {
        PluginHandle handle = requireHandle(code);
        if (handle.state() == PluginState.DESTROYED) {
            return;
        }
        RuntimeException failure = null;
        if (handle.state() != PluginState.DISCOVERED
                && handle.instance() instanceof PluginLifecycle lifecycle) {
            try {
                lifecycle.stop();
            } catch (RuntimeException error) {
                failure = error;
            }
        }
        Closeable closeable = closeables.remove(code);
        if (closeable != null) {
            IOException closeError = releaseCloseable(closeable, closeable != handle.instance());
            if (closeError != null) {
                if (failure == null) {
                    failure = new IllegalStateException("Cannot close plugin resources", closeError);
                }
            }
        }
        replaceState(handle, PluginState.DESTROYED, failure == null ? "" : failure.getMessage());
        registry.remove(code);
        if (failure != null) {
            throw failure;
        }
    }

    public synchronized void loadServices(ClassLoader classLoader) {
        ServiceLoader<AgentPluginProvider> loader = ServiceLoader.load(AgentPluginProvider.class, classLoader);
        for (AgentPluginProvider provider : loader) {
            PluginDescriptor meta = PluginDescriptor.builder()
                    .pluginType(provider.descriptor().pluginType())
                    .code(provider.descriptor().code())
                    .name(provider.descriptor().name())
                    .version(provider.descriptor().version())
                    .description(provider.descriptor().description())
                    .spiVersion(provider.descriptor().spiVersion())
                    .dynamicLoadable(true)
                    .pluginClassLoader(provider.getClass().getClassLoader())
                    .build();
            register(meta, provider, provider.getClass().getClassLoader() instanceof Closeable closeable
                    ? closeable : null);
        }
    }

    public synchronized PluginHandle loadJar(Path jar, ClassLoader parent) {
        if (!Files.isRegularFile(jar)) {
            throw new IllegalArgumentException("Plugin JAR does not exist: " + jar);
        }
        ClassLoader classLoader = sandbox.createClassLoader(jar, parent);
        Set<String> before = new LinkedHashSet<>(handles.keySet());
        try {
            loadServices(classLoader);
        } catch (RuntimeException | ServiceConfigurationError error) {
            rollbackPlugins(before, classLoader);
            throw error;
        }
        if (handles.size() == before.size()) {
            if (classLoader instanceof URLClassLoader urlClassLoader) {
                try {
                    urlClassLoader.close();
                } catch (IOException error) {
                    // No plugin was loaded; there is nothing else to preserve.
                    LOGGER.log(System.Logger.Level.WARNING, "Unable to close empty plugin classloader", error);
                }
            }
            throw new IllegalArgumentException("No AgentPluginProvider service found in " + jar);
        }
        return handles.values().stream()
                .filter(handle -> handle.meta().pluginClassLoader() == classLoader)
                .findFirst()
                .orElseThrow();
    }

    public Optional<PluginHandle> find(String code) {
        return Optional.ofNullable(handles.get(code));
    }

    public Collection<PluginHandle> all() {
        return List.copyOf(handles.values());
    }

    @Override
    public synchronized void close() {
        RuntimeException failure = null;
        for (String code : List.copyOf(handles.keySet())) {
            try {
                destroy(code);
            } catch (RuntimeException error) {
                if (failure == null) {
                    failure = error;
                } else {
                    failure.addSuppressed(error);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private PluginHandle requireHandle(String code) {
        PluginHandle handle = handles.get(code);
        if (handle == null) {
            throw new IllegalArgumentException("Unknown plugin: " + code);
        }
        return handle;
    }

    private void rollbackPlugins(Set<String> before, ClassLoader classLoader) {
        boolean classLoaderTracked = closeableReferences.containsKey(classLoader);
        Set<String> added = new LinkedHashSet<>(handles.keySet());
        added.removeAll(before);
        for (String code : added) {
            handles.remove(code);
            registry.remove(code);
            Closeable closeable = closeables.remove(code);
            if (closeable != null) {
                releaseCloseable(closeable, true);
            }
        }
        boolean stillReferenced = handles.values().stream()
                .anyMatch(handle -> handle.meta().pluginClassLoader() == classLoader);
        if (!stillReferenced
                && !classLoaderTracked
                && classLoader instanceof Closeable closeable) {
            try {
                closeable.close();
            } catch (IOException error) {
                // The original plugin loading failure remains the actionable error.
                LOGGER.log(System.Logger.Level.WARNING, "Unable to close rolled-back plugin resources", error);
            }
        }
    }

    private IOException releaseCloseable(Closeable closeable, boolean close) {
        int references = closeableReferences.getOrDefault(closeable, 0) - 1;
        if (references > 0) {
            closeableReferences.put(closeable, references);
            return null;
        }
        closeableReferences.remove(closeable);
        if (!close) {
            return null;
        }
        try {
            closeable.close();
            return null;
        } catch (IOException error) {
            return error;
        }
    }

    private static String requirePluginCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Plugin code is required");
        }
        return code;
    }

    private PluginHandle replaceState(PluginHandle handle, PluginState state, String failure) {
        PluginHandle replacement = new PluginHandle(handle.meta(), handle.instance(), state, failure);
        handles.put(handle.meta().code(), replacement);
        return replacement;
    }
}
