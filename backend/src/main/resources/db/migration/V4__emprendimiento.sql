CREATE TABLE emprendimiento (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    ubicacion_id UUID NOT NULL UNIQUE REFERENCES ubicacion(id),
    nombre VARCHAR(120) NOT NULL,
    categoria VARCHAR(40) NOT NULL,
    descripcion VARCHAR(500),
    hora_apertura TIME NOT NULL,
    hora_cierre TIME NOT NULL,
    estado VARCHAR(16) NOT NULL CHECK (estado IN ('ACTIVO', 'INACTIVO', 'ELIMINADO')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_emprendimiento_horario CHECK (hora_cierre > hora_apertura)
);

CREATE UNIQUE INDEX uq_emprendimiento_activo_por_usuario
    ON emprendimiento (usuario_id)
    WHERE estado = 'ACTIVO';

CREATE INDEX ix_emprendimiento_activo_categoria
    ON emprendimiento (categoria)
    WHERE estado = 'ACTIVO';
