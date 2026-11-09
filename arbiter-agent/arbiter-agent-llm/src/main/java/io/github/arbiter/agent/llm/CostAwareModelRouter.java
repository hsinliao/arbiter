package io.github.arbiter.agent.llm;

import io.github.arbiter.agent.spi.invocation.RoutingRequirements;
import io.github.arbiter.agent.spi.invocation.RoutingPreference;
import io.github.arbiter.agent.spi.llm.ModelClient;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class CostAwareModelRouter implements ModelRouter {

    private final Map<String, ModelRoute> routes = new ConcurrentHashMap<>();

    public void register(ModelSpec spec, ModelClient client) {
        if (spec == null || client == null) {
            throw new IllegalArgumentException("Model specification and client are required");
        }
        ModelRoute previous = routes.putIfAbsent(spec.code(), new ModelRoute(spec, client));
        if (previous != null) {
            throw new IllegalStateException("Model already registered: " + spec.code());
        }
    }

    public void replace(ModelSpec spec, ModelClient client) {
        if (spec == null || client == null) {
            throw new IllegalArgumentException("Model specification and client are required");
        }
        routes.put(spec.code(), new ModelRoute(spec, client));
    }

    public void unregister(String modelCode) {
        routes.remove(modelCode);
    }

    public Optional<ModelRoute> find(String modelCode) {
        return Optional.ofNullable(routes.get(modelCode));
    }

    @Override
    public ModelRoute select(RoutingRequirements requirements) {
        if (requirements.preferredModel() != null) {
            ModelRoute preferred = routes.get(requirements.preferredModel());
            if (preferred != null && eligible(preferred, requirements)) {
                return preferred;
            }
        }
        Comparator<ModelRoute> comparator = comparator(requirements.preference());
        return routes.values().stream()
                .filter(route -> eligible(route, requirements))
                .sorted(comparator.thenComparing(route -> route.spec().code()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No model satisfies the routing constraints"));
    }

    private static boolean eligible(ModelRoute route, RoutingRequirements requirements) {
        ModelSpec spec = route.spec();
        long estimatedTokens = requirements.estimatedInputTokens() + requirements.maxOutputTokens();
        boolean capabilitiesMatch = spec.capabilities().containsAll(requirements.requiredCapabilities());
        boolean withinBudget = requirements.remainingCost() == null
                || estimateCost(spec, requirements).compareTo(requirements.remainingCost()) <= 0;
        return spec.available() && capabilitiesMatch
                && (estimatedTokens == 0 || estimatedTokens <= spec.maxContextTokens())
                && withinBudget;
    }

    private static Comparator<ModelRoute> comparator(RoutingPreference strategy) {
        Comparator<ModelRoute> byPriority = Comparator.comparingInt(route -> -route.spec().priority());
        return switch (strategy) {
            case LOWEST_COST -> Comparator.comparing(route -> route.spec().inputCostPerMillionTokens()
                    .add(route.spec().outputCostPerMillionTokens()));
            case LOWEST_LATENCY -> Comparator.comparing(route -> route.spec().expectedLatency());
            case PRIORITY -> byPriority;
            case BALANCED -> Comparator
                    .comparingDouble(CostAwareModelRouter::balancedScore)
                    .thenComparing(byPriority);
        };
    }

    private static double balancedScore(ModelRoute route) {
        ModelSpec spec = route.spec();
        double cost = spec.inputCostPerMillionTokens().doubleValue()
                + spec.outputCostPerMillionTokens().doubleValue();
        double latency = spec.expectedLatency().toMillis() / 1000.0;
        double priority = 20.0 - spec.priority();
        return cost * 0.45 + latency * 0.45 + Math.max(0.0, priority) * 0.10;
    }

    private static BigDecimal estimateCost(ModelSpec spec, RoutingRequirements requirements) {
        BigDecimal input = spec.inputCostPerMillionTokens()
                .multiply(BigDecimal.valueOf(requirements.estimatedInputTokens()))
                .divide(BigDecimal.valueOf(1_000_000), MathContext.DECIMAL64);
        BigDecimal output = spec.outputCostPerMillionTokens()
                .multiply(BigDecimal.valueOf(requirements.maxOutputTokens()))
                .divide(BigDecimal.valueOf(1_000_000), MathContext.DECIMAL64);
        return input.add(output);
    }
}
