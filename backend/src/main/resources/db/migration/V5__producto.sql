CREATE TABLE producto (
    id UUID PRIMARY KEY,
    emprendimiento_id UUID NOT NULL REFERENCES emprendimiento(id),
    nombre VARCHAR(120) NOT NULL,
    precio NUMERIC(12, 2) NOT NULL CHECK (precio > 0),
    unidad VARCHAR(40) NOT NULL,
    descripcion VARCHAR(500),
    cantidad_disponible INTEGER NOT NULL CHECK (cantidad_disponible > 0),
    imagen_url TEXT,
    estado VARCHAR(16) NOT NULL CHECK (estado IN ('DISPONIBLE', 'AGOTADO', 'INACTIVO')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uq_producto_nombre_emprendimiento
    ON producto (emprendimiento_id, LOWER(nombre));

CREATE INDEX ix_producto_publicado
    ON producto (emprendimiento_id, estado)
    WHERE estado = 'DISPONIBLE';
