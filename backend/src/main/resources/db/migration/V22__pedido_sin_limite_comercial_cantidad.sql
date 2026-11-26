-- Se conserva NUMERIC(10,2) y cantidad positiva; se elimina el límite comercial de 20.
ALTER TABLE item_pedido DROP CONSTRAINT item_pedido_cantidad_check;
ALTER TABLE item_pedido ADD CONSTRAINT item_pedido_cantidad_check CHECK (cantidad > 0);
