package io.github.arbiter.agent.spi.memory;

import java.util.Arrays;

public record Embedding(double[] values) {

    public Embedding {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Embedding values are required");
        }
        values = values.clone();
    }

    @Override
    public double[] values() {
        return values.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Embedding embedding && Arrays.equals(values, embedding.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }
}
