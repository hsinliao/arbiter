package io.github.arbiter.agent.plugin;

import java.nio.file.Path;

public interface PluginSandbox {

    ClassLoader createClassLoader(Path jar, ClassLoader parent);
}
