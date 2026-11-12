CREATE TABLE pedido (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    emprendimiento_id UUID NOT NULL REFERENCES emprendimiento(id),
    modalidad VARCHAR(24) NOT NULL CHECK (modalidad IN ('ENTREGA_DIRECTA', 'RECOGIDA', 'DOMICILIO')),
    estado VARCHAR(32) NOT NULL CHECK (estado IN (
        'PENDIENTE', 'ACEPTADO', 'RECHAZADO', 'LISTO_PARA_RECOGER', 'EN_CAMINO', 'ENTREGADO', 'CANCELADO'
    )),
    reserva_at TIMESTAMPTZ,
    medio_pago VARCHAR(24) NOT NULL CHECK (medio_pago IN ('EFECTIVO', 'CUENTA_BANCARIA')),
    tarifa_domicilio BIGINT CHECK (tarifa_domicilio IS NULL OR tarifa_domicilio > 0),
    total BIGINT NOT NULL CHECK (total >= 0),
    ubicacion_entrega_id UUID UNIQUE REFERENCES ubicacion(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_pedido_modalidad_datos CHECK (
        (modalidad = 'ENTREGA_DIRECTA' AND ubicacion_entrega_id IS NOT NULL AND tarifa_domicilio IS NULL)
        OR (modalidad = 'RECOGIDA' AND ubicacion_entrega_id IS NULL AND tarifa_domicilio IS NULL)
        OR (modalidad = 'DOMICILIO' AND ubicacion_entrega_id IS NOT NULL
            AND tarifa_domicilio IS NOT NULL AND medio_pago = 'CUENTA_BANCARIA')
    )
);

CREATE TABLE item_pedido (
    id UUID PRIMARY KEY,
    pedido_id UUID NOT NULL REFERENCES pedido(id) ON DELETE CASCADE,
    producto_id UUID NOT NULL REFERENCES producto(id),
    cantidad NUMERIC(10,2) NOT NULL CHECK (cantidad > 0 AND cantidad <= 20),
    precio_unitario BIGINT NOT NULL CHECK (precio_unitario > 0),
    CONSTRAINT uq_item_pedido_producto UNIQUE (pedido_id, producto_id)
);

CREATE INDEX ix_pedido_emprendimiento_created_at ON pedido (emprendimiento_id, created_at DESC);
CREATE INDEX ix_pedido_usuario_created_at ON pedido (usuario_id, created_at DESC);
CREATE INDEX ix_pedido_emprendimiento_estado ON pedido (emprendimiento_id, estado);
CREATE INDEX ix_item_pedido_producto ON item_pedido (producto_id);
