package com.shopchain.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.Properties;

/** Uses Neon DATABASE_URL when configured; explicit SPRING_DATASOURCE_* remains the override. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "DATABASE_URL")
class NeonDataSourceConfiguration {
    @Bean
    @Primary
    HikariDataSource neonDataSource(Environment environment) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        String configuredJdbcUrl = environment.getProperty("SPRING_DATASOURCE_URL");
        NeonDatabaseUrl.ConnectionSettings parsed = StringUtils.hasText(configuredJdbcUrl)
                ? new NeonDatabaseUrl.ConnectionSettings(configuredJdbcUrl,
                        environment.getProperty("SPRING_DATASOURCE_USERNAME"),
                        environment.getProperty("SPRING_DATASOURCE_PASSWORD"))
                : NeonDatabaseUrl.parse(databaseUrl);

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setJdbcUrl(parsed.jdbcUrl());
        dataSource.setUsername(firstText(environment.getProperty("SPRING_DATASOURCE_USERNAME"), parsed.username()));
        dataSource.setPassword(firstText(environment.getProperty("SPRING_DATASOURCE_PASSWORD"), parsed.password()));
        dataSource.setMaximumPoolSize(environment.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class, 5));
        dataSource.setMinimumIdle(environment.getProperty("spring.datasource.hikari.minimum-idle", Integer.class, 1));
        dataSource.setMaxLifetime(environment.getProperty("spring.datasource.hikari.max-lifetime", Long.class, 120_000L));
        dataSource.setIdleTimeout(environment.getProperty("spring.datasource.hikari.idle-timeout", Long.class, 60_000L));
        dataSource.setConnectionTimeout(environment.getProperty("spring.datasource.hikari.connection-timeout", Long.class, 20_000L));
        dataSource.setValidationTimeout(environment.getProperty("spring.datasource.hikari.validation-timeout", Long.class, 3_000L));
        Properties driverProperties = new Properties();
        driverProperties.setProperty("prepareThreshold", "0");
        driverProperties.setProperty("tcpKeepAlive", "true");
        dataSource.setDataSourceProperties(driverProperties);
        return dataSource;
    }

    private String firstText(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }
}
