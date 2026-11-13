CREATE TABLE domicilio (
    id UUID PRIMARY KEY,
    pedido_id UUID NOT NULL REFERENCES pedido(id),
    vendedor_id UUID NOT NULL REFERENCES usuario(id),
    comprador_id UUID NOT NULL REFERENCES usuario(id),
    domiciliario_id UUID REFERENCES usuario(id),
    estado VARCHAR(16) NOT NULL CHECK (estado IN (
        'DISPONIBLE', 'ASIGNADO', 'RECOGIDO', 'EN_CAMINO', 'ENTREGADO', 'CANCELADO'
    )),
    tarifa BIGINT NOT NULL CHECK (tarifa > 0),
    ubicacion_origen_id UUID NOT NULL UNIQUE REFERENCES ubicacion(id),
    ubicacion_destino_id UUID NOT NULL REFERENCES ubicacion(id),
    -- Código de 6 dígitos que ve el cliente (RF-25) y el domiciliario ingresa al entregar (RF-24); nace al asignarse.
    codigo_confirmacion CHAR(6) CHECK (codigo_confirmacion ~ '^[0-9]{6}$'),
    asignado_at TIMESTAMPTZ,
    motivo_cancelacion VARCHAR(24) CHECK (motivo_cancelacion IN ('SIN_ASIGNAR', 'ADMINISTRADOR')),
    aviso_sin_asignar_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_domicilio_domiciliario CHECK (
        (estado = 'DISPONIBLE' AND domiciliario_id IS NULL)
        OR (estado IN ('ASIGNADO', 'RECOGIDO', 'EN_CAMINO', 'ENTREGADO') AND domiciliario_id IS NOT NULL
            AND codigo_confirmacion IS NOT NULL AND asignado_at IS NOT NULL)
        OR estado = 'CANCELADO'
    ),
    CONSTRAINT ck_domicilio_cancelacion CHECK (
        (estado = 'CANCELADO') = (motivo_cancelacion IS NOT NULL)
    )
);

-- Un pedido solo puede tener un domicilio activo (RF-14 FR-005).
CREATE UNIQUE INDEX ux_domicilio_pedido_activo ON domicilio (pedido_id) WHERE estado <> 'CANCELADO';
-- Listado del vendedor (RF-15).
CREATE INDEX ix_domicilio_vendedor_created_at ON domicilio (vendedor_id, created_at DESC, id DESC);
-- Disponibles (RF-16) y tarea de domicilios sin asignar (RF-14 FR-009).
CREATE INDEX ix_domicilio_disponible_created_at ON domicilio (created_at) WHERE estado = 'DISPONIBLE';
-- Domicilios en curso de un domiciliario (RF-19 FR-006, máximo 3).
CREATE INDEX ix_domicilio_domiciliario_estado ON domicilio (domiciliario_id, estado) WHERE domiciliario_id IS NOT NULL;

-- Traza del domicilio (RF-26): inmutable, una fila por cambio de estado.
CREATE TABLE evento_trazabilidad (
    id UUID PRIMARY KEY,
    domicilio_id UUID NOT NULL REFERENCES domicilio(id),
    estado VARCHAR(16) NOT NULL CHECK (estado IN (
        'DISPONIBLE', 'ASIGNADO', 'RECOGIDO', 'EN_CAMINO', 'ENTREGADO', 'CANCELADO'
    )),
    usuario_id UUID REFERENCES usuario(id),
    ocurrido_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_evento_trazabilidad_domicilio ON evento_trazabilidad (domicilio_id, ocurrido_at);

-- Rechazos de un domiciliario (RF-17): el domicilio deja de mostrársele.
CREATE TABLE domicilio_rechazo (
    domiciliario_id UUID NOT NULL REFERENCES usuario(id),
    domicilio_id UUID NOT NULL REFERENCES domicilio(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_domicilio_rechazo PRIMARY KEY (domiciliario_id, domicilio_id)
);

ALTER TABLE pago
    ADD CONSTRAINT fk_pago_domicilio FOREIGN KEY (domicilio_id) REFERENCES domicilio(id);
