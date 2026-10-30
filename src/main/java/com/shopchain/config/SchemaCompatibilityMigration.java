package com.shopchain.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.List;

/** Applies additive/backfill steps after Hibernate has created or updated the current schema. */
@Component
@Order(1)
public class SchemaCompatibilityMigration implements ApplicationRunner {
    private static final List<MoneyColumn> MONEY_COLUMNS = List.of(
            new MoneyColumn("orders", "total_amount"),
            new MoneyColumn("order_items", "price"),
            new MoneyColumn("sales_invoices", "subtotal"),
            new MoneyColumn("sales_invoices", "tax_amount"),
            new MoneyColumn("sales_invoices", "total_amount"),
            new MoneyColumn("sales_invoice_lines", "unit_price"),
            new MoneyColumn("sales_invoice_lines", "line_total"),
            new MoneyColumn("store_reservation_items", "unit_price"));

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, Boolean> tableExistsCache = new HashMap<>();
    private final Map<String, Set<String>> columnCache = new HashMap<>();

    public SchemaCompatibilityMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws SQLException {
        normalizeMoneyColumns();
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
        ensureInventoryLookupIndex();
        ensureReservationOrderConstraint();
    }

    private void normalizeMoneyColumns() {
        Map<String, MoneyColumnType> types = new HashMap<>();
        jdbcTemplate.query("SELECT table_name, column_name, data_type, numeric_precision, numeric_scale "
                        + "FROM information_schema.columns WHERE table_schema = 'public'",
                resultSet -> {
                    Integer precision = resultSet.getObject("numeric_precision") == null
                            ? null : resultSet.getInt("numeric_precision");
                    Integer scale = resultSet.getObject("numeric_scale") == null
                            ? null : resultSet.getInt("numeric_scale");
                    types.put(resultSet.getString("table_name") + "." + resultSet.getString("column_name"),
                            new MoneyColumnType(resultSet.getString("data_type"), precision, scale));
                });

        for (MoneyColumn column : MONEY_COLUMNS) {
            MoneyColumnType type = types.get(column.table() + "." + column.name());
            if (type == null || type.isFixedMoney()) continue;
            jdbcTemplate.execute("ALTER TABLE " + column.table() + " ALTER COLUMN " + column.name()
                    + " TYPE NUMERIC(14,2) USING round(" + column.name() + "::numeric, 2)");
        }
    }

    private void ensureInventoryLookupIndex() throws SQLException {
        if (tableExists("product_inventory") && hasColumns("product_inventory", "store_id", "product_id")) {
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_product_inventory_store_product "
                    + "ON product_inventory(store_id, product_id)");
        }
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
        String key = table.toLowerCase(Locale.ROOT);
        Boolean cached = tableExistsCache.get(key);
        if (cached != null) return cached;
        boolean exists;
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            try (var result = connection.getMetaData().getTables(connection.getCatalog(), "public",
                    key, new String[]{"TABLE"})) {
                exists = result.next();
            }
        }
        tableExistsCache.put(key, exists);
        return exists;
    }

    private boolean hasColumns(String table, String... expected) throws SQLException {
        String key = table.toLowerCase(Locale.ROOT);
        Set<String> columns = columnCache.get(key);
        if (columns == null) {
            columns = new HashSet<>();
            try (var connection = jdbcTemplate.getDataSource().getConnection();
                 var result = connection.getMetaData().getColumns(connection.getCatalog(), "public", key, null)) {
                while (result.next()) columns.add(result.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
            columnCache.put(key, columns);
        }
        for (String column : expected) if (!columns.contains(column.toLowerCase(Locale.ROOT))) return false;
        return true;
    }

    private record MoneyColumn(String table, String name) { }
    private record MoneyColumnType(String dataType, Integer precision, Integer scale) {
        private boolean isFixedMoney() {
            return "numeric".equalsIgnoreCase(dataType) && Integer.valueOf(14).equals(precision)
                    && Integer.valueOf(2).equals(scale);
        }
    }
}
