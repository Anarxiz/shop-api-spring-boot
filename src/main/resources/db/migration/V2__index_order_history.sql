CREATE INDEX idx_orders_user_history ON orders (user_id, created_at DESC, id DESC);
DROP INDEX idx_orders_user_id;
