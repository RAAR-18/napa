-- RF-13: el administrador cancela un domicilio escribiendo el motivo (10 a 300 caracteres). La cancelación
-- automática (SIN_ASIGNAR) no lleva texto.
ALTER TABLE domicilio ADD COLUMN detalle_cancelacion VARCHAR(300);
ALTER TABLE domicilio ADD CONSTRAINT ck_domicilio_detalle_cancelacion CHECK (
    (detalle_cancelacion IS NULL) = (motivo_cancelacion IS DISTINCT FROM 'ADMINISTRADOR')
);

-- Listado del administrador (RF-12): filtros por estado y por domiciliario sobre la fecha de publicación.
CREATE INDEX ix_domicilio_estado_created_at ON domicilio (estado, created_at DESC, id DESC);
CREATE INDEX ix_domicilio_domiciliario_created_at ON domicilio (domiciliario_id, created_at DESC, id DESC)
    WHERE domiciliario_id IS NOT NULL;
CREATE INDEX ix_domicilio_created_at ON domicilio (created_at DESC, id DESC);
