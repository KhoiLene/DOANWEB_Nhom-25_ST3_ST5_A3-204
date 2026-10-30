package com.shopchain.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** Converts Neon's standard PostgreSQL URL into JDBC settings without logging credentials. */
final class NeonDatabaseUrl {
    private NeonDatabaseUrl() {
    }

    static ConnectionSettings parse(String databaseUrl) {
        try {
            String value = databaseUrl == null ? "" : databaseUrl.trim();
            if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (!("postgresql".equalsIgnoreCase(scheme) || "postgres".equalsIgnoreCase(scheme))
                    || uri.getHost() == null || uri.getRawPath() == null || uri.getRawPath().length() < 2) {
                throw new IllegalArgumentException("Unsupported PostgreSQL URL shape");
            }

            String jdbcUrl = "jdbc:postgresql://" + uri.getRawAuthority().substring(
                    uri.getRawAuthority().lastIndexOf('@') + 1)
                    + uri.getRawPath()
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
            String username = null;
            String password = null;
            if (uri.getRawUserInfo() != null) {
                int separator = uri.getRawUserInfo().indexOf(':');
                username = decode(separator < 0 ? uri.getRawUserInfo() : uri.getRawUserInfo().substring(0, separator));
                password = separator < 0 ? null : decode(uri.getRawUserInfo().substring(separator + 1));
            }
            return new ConnectionSettings(jdbcUrl, username, password);
        } catch (RuntimeException ignored) {
            // Never retain the URI parser exception: it may include the supplied credentials.
            throw new IllegalArgumentException("DATABASE_URL must be a valid PostgreSQL connection URL");
        }
    }

    private static String decode(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    record ConnectionSettings(String jdbcUrl, String username, String password) {
    }
}
