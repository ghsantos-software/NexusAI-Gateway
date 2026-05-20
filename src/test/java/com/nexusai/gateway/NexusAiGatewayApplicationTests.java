package com.nexusai.gateway;

import org.junit.jupiter.api.Test;

class NexusAiGatewayApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        // Verifies the full Spring context starts without errors.
        // Requires docker-compose postgres to be running: docker compose up -d postgres
    }
}
