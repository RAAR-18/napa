-- Barrio de la ubicación (obligatorio en la entrega de pedidos a domicilio). El domiciliario lo ve antes de
-- aceptar en lugar de la dirección exacta (RF-16, RF-18).
ALTER TABLE ubicacion ADD COLUMN barrio VARCHAR(100);
