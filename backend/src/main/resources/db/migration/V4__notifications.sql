CREATE TABLE notificacion (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    tipo VARCHAR(64) NOT NULL,
    titulo TEXT NOT NULL,
    cuerpo TEXT NOT NULL,
    leida_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    event_id UUID NOT NULL,
    CONSTRAINT uq_notificacion_evento_usuario UNIQUE (event_id, usuario_id)
);

CREATE INDEX ix_notificacion_usuario_fecha
    ON notificacion (usuario_id, created_at DESC);

CREATE TABLE preferencia_notificacion (
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    tipo VARCHAR(64) NOT NULL,
    habilitada BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_preferencia_notificacion PRIMARY KEY (usuario_id, tipo)
);
