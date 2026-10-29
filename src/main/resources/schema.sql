-- Safe, idempotent schema patch to ensure essential columns exist on PostgreSQL/Supabase
ALTER TABLE IF EXISTS categories ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE IF EXISTS categories ADD COLUMN IF NOT EXISTS image_url TEXT;

ALTER TABLE IF EXISTS products ADD COLUMN IF NOT EXISTS discount_percent NUMERIC(5,2) DEFAULT 0;
ALTER TABLE IF EXISTS products ADD COLUMN IF NOT EXISTS campaign_name VARCHAR(255);
ALTER TABLE IF EXISTS products ADD COLUMN IF NOT EXISTS campaign_start_at TIMESTAMP;
ALTER TABLE IF EXISTS products ADD COLUMN IF NOT EXISTS campaign_end_at TIMESTAMP;
ALTER TABLE IF EXISTS products ADD COLUMN IF NOT EXISTS brand_name VARCHAR(255);
ALTER TABLE IF EXISTS products ADD COLUMN IF NOT EXISTS suitable_for TEXT;
ALTER TABLE IF EXISTS products ADD COLUMN IF NOT EXISTS usage_instructions TEXT;
ALTER TABLE IF EXISTS products ADD COLUMN IF NOT EXISTS ingredients TEXT;
ALTER TABLE IF EXISTS products ADD COLUMN IF NOT EXISTS product_cautions TEXT;
ALTER TABLE IF EXISTS products ADD COLUMN IF NOT EXISTS category_id BIGINT;

ALTER TABLE IF EXISTS reviews ADD COLUMN IF NOT EXISTS reviewer_phone VARCHAR(50);

ALTER TABLE IF EXISTS users ADD COLUMN IF NOT EXISTS store_id BIGINT;
ALTER TABLE IF EXISTS users ADD COLUMN IF NOT EXISTS warehouse_id BIGINT;

ALTER TABLE IF EXISTS orders ADD COLUMN IF NOT EXISTS store_id BIGINT;
ALTER TABLE IF EXISTS orders ADD COLUMN IF NOT EXISTS total_amount NUMERIC(12,2) DEFAULT 0;
ALTER TABLE IF EXISTS orders ADD COLUMN IF NOT EXISTS fulfillment_type VARCHAR(50);
ALTER TABLE IF EXISTS orders ADD COLUMN IF NOT EXISTS delivery_address TEXT;
ALTER TABLE IF EXISTS orders ADD COLUMN IF NOT EXISTS delivery_city VARCHAR(100);
ALTER TABLE IF EXISTS orders ADD COLUMN IF NOT EXISTS fulfillment_warehouse_id BIGINT;
ALTER TABLE IF EXISTS orders ADD COLUMN IF NOT EXISTS subtotal_amount NUMERIC(14,2);
ALTER TABLE IF EXISTS orders ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(14,2);
ALTER TABLE IF EXISTS orders ADD COLUMN IF NOT EXISTS shipping_amount NUMERIC(14,2);
ALTER TABLE IF EXISTS orders ADD COLUMN IF NOT EXISTS payment_expires_at TIMESTAMP;
ALTER TABLE IF EXISTS order_items ADD COLUMN IF NOT EXISTS list_unit_price NUMERIC(14,2);

ALTER TABLE IF EXISTS sales_invoices ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(14,2) DEFAULT 0;
ALTER TABLE IF EXISTS sales_invoices ADD COLUMN IF NOT EXISTS shipping_amount NUMERIC(14,2) DEFAULT 0;
ALTER TABLE IF EXISTS sales_invoice_lines ADD COLUMN IF NOT EXISTS list_unit_price NUMERIC(14,2);

-- Convert legacy floating point money columns to fixed precision before JPA uses BigDecimal.
ALTER TABLE IF EXISTS orders ALTER COLUMN total_amount TYPE NUMERIC(14,2) USING round(total_amount::numeric, 2);
ALTER TABLE IF EXISTS order_items ALTER COLUMN price TYPE NUMERIC(14,2) USING round(price::numeric, 2);
ALTER TABLE IF EXISTS sales_invoices ALTER COLUMN subtotal TYPE NUMERIC(14,2) USING round(subtotal::numeric, 2);
ALTER TABLE IF EXISTS sales_invoices ALTER COLUMN tax_amount TYPE NUMERIC(14,2) USING round(tax_amount::numeric, 2);
ALTER TABLE IF EXISTS sales_invoices ALTER COLUMN total_amount TYPE NUMERIC(14,2) USING round(total_amount::numeric, 2);
ALTER TABLE IF EXISTS sales_invoice_lines ALTER COLUMN unit_price TYPE NUMERIC(14,2) USING round(unit_price::numeric, 2);
ALTER TABLE IF EXISTS sales_invoice_lines ALTER COLUMN line_total TYPE NUMERIC(14,2) USING round(line_total::numeric, 2);

-- Safe backfill for existing orders and receipts. Historical shipping/discount breakdown was not stored.
DO $$
BEGIN
    IF to_regclass('public.orders') IS NOT NULL THEN
        UPDATE orders SET subtotal_amount = total_amount, discount_amount = 0, shipping_amount = 0
        WHERE subtotal_amount IS NULL;
        UPDATE orders SET payment_expires_at = order_date + INTERVAL '15 minutes'
        WHERE payment_expires_at IS NULL AND upper(coalesce(payment_method, '')) = 'VNPAY'
          AND upper(coalesce(payment_status, '')) = 'UNPAID' AND upper(coalesce(status, '')) = 'PENDING';
    END IF;
    IF to_regclass('public.order_items') IS NOT NULL THEN
        UPDATE order_items SET list_unit_price = price WHERE list_unit_price IS NULL;
    END IF;
    IF to_regclass('public.sales_invoice_lines') IS NOT NULL THEN
        UPDATE sales_invoice_lines SET list_unit_price = unit_price WHERE list_unit_price IS NULL;
    END IF;
END $$;
