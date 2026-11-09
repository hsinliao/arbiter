package io.github.arbiter.agent.plugin;

import io.github.arbiter.agent.core.PluginRegistry;
import io.github.arbiter.agent.spi.annotation.AgentPlugin;
import io.github.arbiter.agent.spi.lifecycle.PluginLifecycle;
import io.github.arbiter.agent.spi.model.PluginConfig;
import io.github.arbiter.agent.spi.model.PluginDescriptor;
import org.junit.jupiter.api.Test;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginManagerTest {

    @Test
    void registersAndRunsLifecycle() {
        PluginRegistry registry = new PluginRegistry();
        SamplePlugin plugin = new SamplePlugin();
        try (PluginManager manager = new PluginManager(registry)) {
            manager.registerAnnotated(plugin);
            manager.initialize("sample", new PluginConfig("wrong-code", Map.of("enabled", true)));

            assertEquals(PluginState.INITIALIZED, manager.find("sample").orElseThrow().state());
            assertTrue(plugin.initialized);
            assertEquals("sample", plugin.configuredCode);
            assertTrue(registry.find("sample").isPresent());
            manager.destroy("sample");
            assertTrue(plugin.destroyed);
            assertTrue(registry.find("sample").isEmpty());
            assertThrows(IllegalStateException.class, () -> manager.initialize("sample", null));
        }
    }

    @Test
    void closesSharedPluginResourceAfterLastPlugin() {
        TrackingCloseable closeable = new TrackingCloseable();
        try (PluginManager manager = new PluginManager(new PluginRegistry())) {
            manager.register(PluginDescriptor.builder().pluginType("tool").code("one").name("One").build(),
                    new SamplePlugin(), closeable);
            manager.register(PluginDescriptor.builder().pluginType("tool").code("two").name("Two").build(),
                    new SamplePlugin(), closeable);

            manager.destroy("one");
            assertFalse(closeable.closed.get());
            manager.destroy("two");
            assertTrue(closeable.closed.get());
        }
    }

    @Test
    void rejectsIncompatibleSpiMajorVersion() {
        try (PluginManager manager = new PluginManager(new PluginRegistry())) {
            PluginDescriptor descriptor = PluginDescriptor.builder()
                    .pluginType("tool")
                    .code("old")
                    .name("Old plugin")
                    .spiVersion(1)
                    .build();

            assertThrows(IllegalArgumentException.class,
                    () -> manager.register(descriptor, new SamplePlugin(), null));
        }
    }

    @AgentPlugin(type = "tool", code = "sample", name = "Sample")
    static final class SamplePlugin implements PluginLifecycle {
        private boolean initialized;
        private boolean destroyed;
        private String configuredCode;

        @Override
        public void start(PluginConfig config) {
            initialized = config.get("enabled", Boolean.class);
            configuredCode = config.getPluginCode();
        }

        @Override
        public void stop() {
            destroyed = true;
        }
    }

    private static final class TrackingCloseable implements Closeable {
        private final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public void close() throws IOException {
            closed.set(true);
        }
    }
}
