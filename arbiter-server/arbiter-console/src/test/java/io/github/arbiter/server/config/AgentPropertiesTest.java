package io.github.arbiter.server.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentPropertiesTest {

    @Test
    void disabledAssistantDoesNotRequireProviderConfiguration() {
        assertDoesNotThrow(() -> new AgentProperties().validate());
    }

    @Test
    void enabledAssistantRequiresServerSideProviderConfiguration() {
        AgentProperties properties = new AgentProperties();
        properties.setEnabled(true);

        assertThrows(IllegalArgumentException.class, properties::validate);

        properties.setApiKey("server-secret");
        assertThrows(IllegalArgumentException.class, properties::validate);

        properties.setModel("test-model");
        properties.setBaseUrl("not-a-url");
        assertThrows(IllegalArgumentException.class, properties::validate);

        properties.setBaseUrl("https://example.test/v1");
        assertDoesNotThrow(properties::validate);
    }
}
