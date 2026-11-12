CREATE TABLE configuracion_pago (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL UNIQUE REFERENCES usuario(id),
    acepta_efectivo BOOLEAN NOT NULL DEFAULT FALSE,
    banco VARCHAR(100),
    numero_cuenta_cifrada VARCHAR(256),
    tipo_cuenta VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT configuracion_pago_tipo_cuenta_check
        CHECK (tipo_cuenta IS NULL OR tipo_cuenta IN ('AHORROS', 'CORRIENTE')),
    CONSTRAINT configuracion_pago_cuenta_completa_check
        CHECK (
            (banco IS NULL AND numero_cuenta_cifrada IS NULL AND tipo_cuenta IS NULL)
            OR (banco IS NOT NULL AND numero_cuenta_cifrada IS NOT NULL AND tipo_cuenta IS NOT NULL)
        )
);
