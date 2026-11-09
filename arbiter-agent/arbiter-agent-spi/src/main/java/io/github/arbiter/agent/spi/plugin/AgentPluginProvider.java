package io.github.arbiter.agent.spi.plugin;

import io.github.arbiter.agent.spi.lifecycle.PluginLifecycle;
import io.github.arbiter.agent.spi.model.PluginDescriptor;

/**
 * ServiceLoader entry point for classpath and JAR plugins.
 */
public interface AgentPluginProvider extends PluginLifecycle {

    PluginDescriptor descriptor();
}
