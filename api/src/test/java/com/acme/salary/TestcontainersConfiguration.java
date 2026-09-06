package com.acme.salary;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    // Pinned to the same image as docker-compose.yml. "latest" would make test
    // results depend on when the image was last pulled.
    return new PostgreSQLContainer(
        DockerImageName.parse("postgres:16-alpine").asCompatibleSubstituteFor("postgres"));
  }
}
