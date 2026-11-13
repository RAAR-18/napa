ALTER TABLE ubicacion
    ALTER COLUMN direccion DROP NOT NULL;

CREATE TABLE emprendimiento (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    ubicacion_id UUID NOT NULL UNIQUE REFERENCES ubicacion(id),
    nombre VARCHAR(100) NOT NULL,
    categoria VARCHAR(32) NOT NULL CHECK (categoria IN (
        'FRUTAS_VERDURAS',
        'PESCADO_MARISCOS',
        'ALIMENTOS_PREPARADOS',
        'BEBIDAS',
        'SNACKS',
        'OTROS'
    )),
    descripcion VARCHAR(500) NOT NULL,
    hora_apertura TIME NOT NULL,
    hora_cierre TIME NOT NULL,
    estado VARCHAR(16) NOT NULL CHECK (estado IN ('ACTIVO', 'INACTIVO', 'ELIMINADO')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_emprendimiento_horario CHECK (hora_apertura <> hora_cierre)
);

CREATE UNIQUE INDEX uq_emprendimiento_activo_por_usuario
    ON emprendimiento (usuario_id)
    WHERE estado = 'ACTIVO';
