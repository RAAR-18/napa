-- RF-70: registro de cada envío de código de verificación.
-- Nunca se guarda el código: lo genera y valida el proveedor.
CREATE TABLE mensaje (
                         id UUID PRIMARY KEY,
                         canal VARCHAR(16) NOT NULL CHECK (canal IN ('WHATSAPP', 'SMS')),
                         proposito VARCHAR(16) NOT NULL CHECK (proposito IN ('REGISTRO', 'RECUPERACION', 'CAMBIO')),
                         destino VARCHAR(16) NOT NULL CHECK (destino ~ '^[+][1-9][0-9]{7,14}$'),
    estado VARCHAR(16) NOT NULL CHECK (estado IN ('ENVIADO', 'ENTREGADO', 'FALLIDO')),
    proveedor_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_mensaje_destino_creado ON mensaje (destino, created_at DESC);