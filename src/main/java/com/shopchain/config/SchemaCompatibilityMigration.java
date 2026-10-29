package com.shopchain.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.Locale;

/** Applies additive/backfill steps after Hibernate has created or updated the current schema. */
@Component
public class SchemaCompatibilityMigration implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public SchemaCompatibilityMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws SQLException {
        if (tableExists("orders") && hasColumns("orders", "subtotal_amount", "discount_amount", "shipping_amount", "total_amount")) {
            jdbcTemplate.update("UPDATE orders SET subtotal_amount = total_amount, discount_amount = 0, shipping_amount = 0 WHERE subtotal_amount IS NULL");
        }
        if (tableExists("orders") && hasColumns("orders", "payment_expires_at", "order_date", "payment_method", "payment_status", "status")) {
            jdbcTemplate.update("UPDATE orders SET payment_expires_at = order_date + INTERVAL '15 minutes' "
                    + "WHERE payment_expires_at IS NULL AND upper(coalesce(payment_method, '')) = 'VNPAY' "
                    + "AND upper(coalesce(payment_status, '')) = 'UNPAID' AND upper(coalesce(status, '')) = 'PENDING'");
        }
        backfillPrice("order_items", "list_unit_price", "price");
        backfillPrice("sales_invoice_lines", "list_unit_price", "unit_price");
        backfillPrice("store_reservation_items", "list_unit_price", "unit_price");
        ensureReservationOrderConstraint();
    }

    private void backfillPrice(String table, String target, String source) throws SQLException {
        if (tableExists(table) && hasColumns(table, target, source))
            jdbcTemplate.update("UPDATE " + table + " SET " + target + " = " + source + " WHERE " + target + " IS NULL");
    }

    private void ensureReservationOrderConstraint() throws SQLException {
        if (!tableExists("store_reservations") || !hasColumns("store_reservations", "order_id") || !tableExists("orders")) return;
        if (!hasUniqueIndex("store_reservations", "order_id"))
            jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_store_reservations_order_id "
                    + "ON store_reservations(order_id) WHERE order_id IS NOT NULL");
        if (!hasForeignKey("store_reservations", "order_id", "orders", "id")) {
            jdbcTemplate.execute("ALTER TABLE store_reservations ADD CONSTRAINT fk_store_reservations_order "
                    + "FOREIGN KEY (order_id) REFERENCES orders(id)");
        }
    }

    private boolean hasUniqueIndex(String table, String column) throws SQLException {
        try (var connection = jdbcTemplate.getDataSource().getConnection();
             var indexes = connection.getMetaData().getIndexInfo(connection.getCatalog(), "public", table, true, false)) {
            while (indexes.next()) {
                if (!indexes.getBoolean("NON_UNIQUE") && column.equalsIgnoreCase(indexes.getString("COLUMN_NAME"))) return true;
            }
            return false;
        }
    }

    private boolean hasForeignKey(String table, String column, String referencedTable, String referencedColumn) throws SQLException {
        try (var connection = jdbcTemplate.getDataSource().getConnection();
             var keys = connection.getMetaData().getImportedKeys(connection.getCatalog(), "public", table)) {
            while (keys.next()) {
                if (column.equalsIgnoreCase(keys.getString("FKCOLUMN_NAME"))
                        && referencedTable.equalsIgnoreCase(keys.getString("PKTABLE_NAME"))
                        && referencedColumn.equalsIgnoreCase(keys.getString("PKCOLUMN_NAME"))) return true;
            }
            return false;
        }
    }

    private boolean tableExists(String table) throws SQLException {
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            try (var result = connection.getMetaData().getTables(connection.getCatalog(), "public",
                    table.toLowerCase(Locale.ROOT), new String[]{"TABLE"})) {
                return result.next();
            }
        }
    }

    private boolean hasColumns(String table, String... expected) throws SQLException {
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            var metadata = connection.getMetaData();
            for (String column : expected) {
                boolean found;
                try (var result = metadata.getColumns(connection.getCatalog(), "public",
                        table.toLowerCase(Locale.ROOT), column.toLowerCase(Locale.ROOT))) {
                    found = result.next();
                }
                if (!found) return false;
            }
            return true;
        }
    }
}
