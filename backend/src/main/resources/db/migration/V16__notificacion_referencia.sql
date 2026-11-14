-- RF-38: a qué pedido o domicilio se refiere la notificación, para abrirlo desde la app. Las de reportes y
-- comentarios no llevan referencia.
ALTER TABLE notificacion ADD COLUMN referencia_tipo VARCHAR(16)
    CHECK (referencia_tipo IN ('PEDIDO', 'DOMICILIO'));
ALTER TABLE notificacion ADD COLUMN referencia_id UUID;
ALTER TABLE notificacion ADD CONSTRAINT ck_notificacion_referencia CHECK (
    (referencia_tipo IS NULL) = (referencia_id IS NULL)
);

-- Contador de no leídas de la app.
CREATE INDEX ix_notificacion_usuario_no_leida ON notificacion (usuario_id) WHERE leida_at IS NULL;
