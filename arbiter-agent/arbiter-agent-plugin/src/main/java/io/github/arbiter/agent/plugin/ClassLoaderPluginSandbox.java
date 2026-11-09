package io.github.arbiter.agent.plugin;

import io.github.arbiter.agent.spi.plugin.AgentPluginProvider;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Child-first classloader isolation. This is not an operating-system security boundary.
 */
public final class ClassLoaderPluginSandbox implements PluginSandbox {

    @Override
    public ClassLoader createClassLoader(Path jar, ClassLoader parent) {
        Objects.requireNonNull(jar, "jar");
        try {
            return new IsolatedPluginClassLoader(
                    new URL[]{jar.toAbsolutePath().toUri().toURL()},
                    parent == null ? AgentPluginProvider.class.getClassLoader() : parent);
        } catch (IOException error) {
            throw new IllegalArgumentException("Cannot load plugin: " + jar, error);
        }
    }

    private static final class IsolatedPluginClassLoader extends URLClassLoader {

        private IsolatedPluginClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("java.") || name.startsWith("javax.")
                    || name.startsWith("jdk.") || name.startsWith("io.github.arbiter.agent.spi.")) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        loaded = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}
