-- RF-01 a RF-06: calificaciones y comentarios entre las partes de un pedido entregado.
CREATE TABLE calificacion (
    id UUID PRIMARY KEY,
    autor_id UUID NOT NULL REFERENCES usuario(id),
    pedido_id UUID NOT NULL REFERENCES pedido(id),
    emprendimiento_id UUID REFERENCES emprendimiento(id),
    producto_id UUID REFERENCES producto(id),
    usuario_id UUID REFERENCES usuario(id),
    puntaje SMALLINT NOT NULL CHECK (puntaje BETWEEN 1 AND 5),
    comentario VARCHAR(300) CHECK (comentario IS NULL OR length(btrim(comentario)) BETWEEN 1 AND 300),
    estado VARCHAR(24) NOT NULL CHECK (estado IN ('ACTIVA', 'ELIMINADA_AUTOR', 'ELIMINADA_MODERACION')),
    moderada BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_calificacion_un_destino CHECK (
        num_nonnulls(emprendimiento_id, producto_id, usuario_id) = 1
    )
);

-- FR-006: una calificación por autor, pedido y destino (también las eliminadas: no se vuelve a calificar).
CREATE UNIQUE INDEX ux_calificacion_autor_pedido_destino ON calificacion (
    autor_id,
    pedido_id,
    COALESCE(emprendimiento_id, '00000000-0000-0000-0000-000000000000'::uuid),
    COALESCE(producto_id, '00000000-0000-0000-0000-000000000000'::uuid),
    COALESCE(usuario_id, '00000000-0000-0000-0000-000000000000'::uuid)
);

CREATE INDEX ix_calificacion_emprendimiento ON calificacion (emprendimiento_id, estado, created_at DESC)
    WHERE emprendimiento_id IS NOT NULL;
CREATE INDEX ix_calificacion_producto ON calificacion (producto_id, estado, created_at DESC)
    WHERE producto_id IS NOT NULL;
CREATE INDEX ix_calificacion_usuario ON calificacion (usuario_id, estado, created_at DESC)
    WHERE usuario_id IS NOT NULL;
CREATE INDEX ix_calificacion_estado_created_at ON calificacion (estado, created_at DESC, id DESC);

-- RF-06 FR-004: evidencia de cada acción de moderación, sin updated_at (solo se inserta).
CREATE TABLE moderacion_comentario (
    id UUID PRIMARY KEY,
    calificacion_id UUID NOT NULL REFERENCES calificacion(id),
    administrador_id UUID NOT NULL REFERENCES usuario(id),
    accion VARCHAR(16) NOT NULL CHECK (accion IN ('EDITADO', 'ELIMINADO')),
    motivo VARCHAR(200) NOT NULL CHECK (length(btrim(motivo)) BETWEEN 5 AND 200),
    contenido_anterior VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_moderacion_comentario_calificacion ON moderacion_comentario (calificacion_id, created_at);
