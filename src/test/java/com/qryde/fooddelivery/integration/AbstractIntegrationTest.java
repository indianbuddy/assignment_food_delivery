package com.qryde.fooddelivery.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for integration tests that need a real PostgreSQL instance.
 *
 * Deliberately NOT annotated @Transactional: the concurrency tests need
 * genuinely separate, independently-committed transactions racing each other
 * on real threads, which Spring Test's per-test transaction rollback wrapper
 * would defeat.
 *
 * Deliberately NOT using @Testcontainers/@Container: that ties the
 * container's lifecycle to each test *class* (started/stopped per class),
 * while Spring's TestContext framework caches and reuses one
 * ApplicationContext across all classes that share this identical
 * configuration. Mixing the two means the container gets torn down after the
 * first class's tests finish while later classes keep reusing the cached
 * context's now-dangling connection details. Instead this follows
 * Testcontainers' documented "singleton container" pattern: start it once,
 * manually, and let the JVM shutdown (or the Ryuk sidecar, where available)
 * clean it up.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

	@ServiceConnection
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	static {
		postgres.start();
	}
}
