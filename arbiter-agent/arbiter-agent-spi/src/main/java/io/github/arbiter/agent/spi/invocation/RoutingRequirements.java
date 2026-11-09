package io.github.arbiter.agent.spi.invocation;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Control-plane constraints used by model routing. They are not sent to providers.
 */
public record RoutingRequirements(
        String preferredModel,
        Set<String> requiredCapabilities,
        long estimatedInputTokens,
        int maxOutputTokens,
        BigDecimal remainingCost,
        RoutingPreference preference
) {
    public RoutingRequirements {
        requiredCapabilities = requiredCapabilities == null ? Set.of() : Set.copyOf(requiredCapabilities);
        estimatedInputTokens = Math.max(0, estimatedInputTokens);
        maxOutputTokens = Math.max(0, maxOutputTokens);
        if (remainingCost != null && remainingCost.signum() < 0) {
            throw new IllegalArgumentException("remainingCost must not be negative");
        }
        preference = preference == null ? RoutingPreference.BALANCED : preference;
    }

    public static RoutingRequirements unlimited(String preferredModel) {
        return new RoutingRequirements(
                preferredModel, Set.of(), 0, 0, null, RoutingPreference.BALANCED);
    }
}
