package com.nexusai.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for integration tests.
 *
 * Requires the docker-compose postgres to be running:
 *   docker compose up -d postgres
 *
 * The 'test' profile points to localhost:5433/nexusai (Docker postgres on port 5433).
 * Tables are truncated before each test to ensure a clean state.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetDatabase() {
        // Truncate in dependency order; CASCADE handles any remaining FK constraints.
        jdbcTemplate.execute(
                "TRUNCATE TABLE audit_logs, document_chunks, dlp_rules, users, tenants CASCADE"
        );
    }
}
