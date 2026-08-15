package org.rentez.catalogservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Backs every {@code @SpringBootTest} with a real MySQL 8 container.
 *
 * <p>This is not optional scaffolding. The service declares a datasource, so
 * Hibernate opens a JDBC connection during startup purely to resolve the SQL
 * dialect - meaning {@code contextLoads} fails on any machine that is not
 * already running {@code make infra}, including every CI runner. It is also the
 * only place the Flyway migrations are ever executed automatically.
 *
 * <p>docs/ch01 forbids substituting H2, and this codebase has already paid for
 * that once: {@code Car.model_year} exists solely because {@code year} is
 * reserved in H2 2.x but not in MySQL.
 *
 * <p>Note {@code org.testcontainers.mysql.MySQLContainer} - Testcontainers 2.x
 * moved it out of {@code org.testcontainers.containers} and dropped the
 * self-referencing generic, so there is no diamond here. The old class still
 * ships as a deprecated alias, which is why a wrong import compiles and then
 * misbehaves rather than failing outright.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	MySQLContainer mysqlContainer() {
		return new MySQLContainer("mysql:8.0");
	}

}
