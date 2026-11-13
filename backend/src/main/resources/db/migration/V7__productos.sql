CREATE TABLE producto (
    id UUID PRIMARY KEY,
    emprendimiento_id UUID NOT NULL REFERENCES emprendimiento(id),
    nombre VARCHAR(100) NOT NULL CHECK (length(btrim(nombre)) > 0),
    precio BIGINT NOT NULL CHECK (precio > 0 AND precio <= 9007199254740991),
    unidad VARCHAR(16) NOT NULL CHECK (unidad IN ('UNIDAD', 'KG', 'LIBRA', 'LITRO', 'PORCION')),
    descripcion VARCHAR(500),
    cantidad_disponible NUMERIC(10,2) NOT NULL CHECK (cantidad_disponible >= 0),
    estado VARCHAR(16) NOT NULL CHECK (estado IN ('DISPONIBLE', 'AGOTADO', 'ELIMINADO')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_producto_disponibilidad CHECK (
        estado = 'ELIMINADO'
        OR (estado = 'AGOTADO' AND cantidad_disponible = 0)
        OR (estado = 'DISPONIBLE' AND cantidad_disponible > 0)
    )
);

CREATE UNIQUE INDEX uq_producto_nombre_activo
    ON producto (emprendimiento_id, lower(btrim(nombre))) WHERE estado <> 'ELIMINADO';
CREATE INDEX ix_producto_catalogo ON producto (emprendimiento_id, estado, nombre, id)
    WHERE estado <> 'ELIMINADO';
