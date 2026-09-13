package org.rentez.accountservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Backs every {@code @SpringBootTest} with a real PostgreSQL 16 container.
 *
 * <p>This is not optional scaffolding. The service declares a datasource, so
 * Hibernate opens a JDBC connection during startup purely to resolve the SQL
 * dialect - meaning {@code contextLoads} fails on any machine that is not
 * already running {@code make infra}, including every CI runner. It is also the
 * only place the Flyway migrations are ever executed automatically.
 *
 * <p>docs/ch01 forbids substituting H2, and this codebase has already paid for
 * that once: {@code Car.model_year} exists solely because {@code year} is
 * reserved in H2 2.x. The port from MySQL to Postgres is the second time it has
 * paid off - a partial unique index, JSONB and identity columns all behave
 * differently enough that an in-memory stand-in would have proved nothing.
 *
 * <p>The container has no {@code db/init/01-schemas.sql}, so there is no
 * {@code rentez_*} schema and no per-service role here. Migrations therefore
 * run into {@code public}, the default {@code search_path} for the throwaway
 * test role, which is exactly why {@code spring.flyway.schemas} is left unset
 * in application.properties.
 *
 * <p>Note {@code org.testcontainers.postgresql.PostgreSQLContainer} -
 * Testcontainers 2.x moved it out of {@code org.testcontainers.containers} and
 * dropped the self-referencing generic, so there is no diamond here. The old
 * class still ships as a deprecated alias, which is why a wrong import compiles
 * and then misbehaves rather than failing outright.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer("postgres:16-alpine");
	}

}
