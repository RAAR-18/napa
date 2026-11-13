CREATE TABLE pago (
    id UUID PRIMARY KEY,
    tipo VARCHAR(24) NOT NULL CHECK (tipo IN ('PEDIDO', 'TARIFA_DOMICILIO')),
    monto BIGINT NOT NULL CHECK (monto > 0),
    medio VARCHAR(24) NOT NULL CHECK (medio IN ('EFECTIVO', 'CUENTA_BANCARIA')),
    estado VARCHAR(32) NOT NULL CHECK (estado IN ('PENDIENTE_CONFIRMACION', 'REGISTRADO', 'CONFIRMADO')),
    confirmado_at TIMESTAMPTZ,
    pedido_id UUID UNIQUE REFERENCES pedido(id),
    domicilio_id UUID UNIQUE,
    comprador_id UUID NOT NULL REFERENCES usuario(id),
    vendedor_id UUID NOT NULL REFERENCES usuario(id),
    domiciliario_id UUID REFERENCES usuario(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_pago_referencia CHECK (
        (tipo = 'PEDIDO' AND pedido_id IS NOT NULL AND domicilio_id IS NULL)
        OR (tipo = 'TARIFA_DOMICILIO' AND pedido_id IS NULL AND domicilio_id IS NOT NULL)
    ),
    CONSTRAINT ck_pago_tarifa_medio CHECK (
        tipo <> 'TARIFA_DOMICILIO' OR medio = 'CUENTA_BANCARIA'
    )
);

CREATE INDEX ix_pago_comprador_created_at ON pago (comprador_id, created_at DESC);
CREATE INDEX ix_pago_vendedor_created_at ON pago (vendedor_id, created_at DESC);
CREATE INDEX ix_pago_domiciliario_created_at ON pago (domiciliario_id, created_at DESC);
