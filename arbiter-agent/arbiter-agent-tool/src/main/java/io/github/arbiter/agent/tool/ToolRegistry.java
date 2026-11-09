package io.github.arbiter.agent.tool;

import io.github.arbiter.agent.spi.model.ToolDefinition;
import io.github.arbiter.agent.spi.tool.AgentTool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ToolRegistry {

    private final Map<String, AgentTool> tools = new ConcurrentHashMap<>();

    public void register(AgentTool tool) {
        if (tool == null || tool.definition() == null) {
            throw new IllegalArgumentException("Tool and definition are required");
        }
        ToolDefinition definition = tool.definition();
        if (definition.name() == null || !definition.name().matches("[A-Za-z][A-Za-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("Invalid tool name: " + definition.name());
        }
        AgentTool previous = tools.putIfAbsent(definition.name(), tool);
        if (previous != null) {
            throw new IllegalStateException("Tool already registered: " + definition.name());
        }
    }

    public void replace(AgentTool tool) {
        tools.put(requireName(tool), tool);
    }

    public Optional<AgentTool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public AgentTool require(String name) {
        return find(name).orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + name));
    }

    public boolean remove(String name) {
        return tools.remove(name) != null;
    }

    public Collection<AgentTool> all() {
        return List.copyOf(tools.values());
    }

    public Set<String> groups() {
        Set<String> groups = new LinkedHashSet<>();
        tools.values().forEach(tool -> groups.add(tool.group()));
        return Collections.unmodifiableSet(groups);
    }

    public List<AgentTool> byGroups(Set<String> groups) {
        return tools.values().stream()
                .filter(tool -> groups == null || groups.isEmpty() || groups.contains(tool.group()))
                .sorted((left, right) -> left.definition().name().compareTo(right.definition().name()))
                .toList();
    }

    public List<ToolDefinition> definitions(Set<String> groups) {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (AgentTool tool : byGroups(groups)) {
            definitions.add(tool.definition());
        }
        return List.copyOf(definitions);
    }

    private static String requireName(AgentTool tool) {
        if (tool == null || tool.definition() == null
                || tool.definition().name() == null
                || !tool.definition().name().matches("[A-Za-z][A-Za-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("Invalid tool name");
        }
        return tool.definition().name();
    }
}
