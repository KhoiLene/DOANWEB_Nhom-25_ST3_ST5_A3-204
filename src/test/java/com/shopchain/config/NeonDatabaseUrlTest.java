package com.shopchain.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NeonDatabaseUrlTest {
    @Test
    void convertsNeonUrlToJdbcAndKeepsTlsParameters() {
        NeonDatabaseUrl.ConnectionSettings settings = NeonDatabaseUrl.parse(
                "\"postgresql://db_user:p%40ss+word@ep-example-pooler.eu-west-2.aws.neon.tech/neondb"
                        + "?sslmode=require&channel_binding=require\"");

        assertEquals("jdbc:postgresql://ep-example-pooler.eu-west-2.aws.neon.tech/neondb"
                + "?sslmode=require&channel_binding=require", settings.jdbcUrl());
        assertEquals("db_user", settings.username());
        assertEquals("p@ss+word", settings.password());
    }

    @Test
    void rejectsNonPostgresUrlsWithoutIncludingCredentialsInMessage() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> NeonDatabaseUrl.parse("mysql://private-user:private-password@db.example/neondb"));

        org.junit.jupiter.api.Assertions.assertFalse(exception.getMessage().contains("private-password"));
    }

    @Test
    void datasourceUsesNeonUrlAndAllowsExplicitJdbcOverride() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "DATABASE_URL", "postgresql://neon-user:neon-pass@ep-example.eu-west-2.aws.neon.tech/neondb?sslmode=require",
                "SPRING_DATASOURCE_URL", "jdbc:postgresql://override.example:5432/app",
                "SPRING_DATASOURCE_USERNAME", "override-user",
                "SPRING_DATASOURCE_PASSWORD", "override-pass")));

        try (var dataSource = new NeonDataSourceConfiguration().neonDataSource(environment)) {
            assertEquals("jdbc:postgresql://override.example:5432/app", dataSource.getJdbcUrl());
            assertEquals("override-user", dataSource.getUsername());
            assertEquals("override-pass", dataSource.getPassword());
        }
    }
}
