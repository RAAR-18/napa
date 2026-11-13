CREATE TABLE usuario (
    id UUID PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL,
    celular VARCHAR(16) NOT NULL,
    correo VARCHAR(254),
    password_hash VARCHAR(255) NOT NULL,
    foto_url TEXT,
    rol VARCHAR(32) NOT NULL CHECK (rol IN (
        'CLIENTE', 'VENDEDOR_AMBULANTE', 'VENDEDOR_PUNTO_FIJO', 'DOMICILIARIO', 'ADMINISTRADOR'
    )),
    estado VARCHAR(32) NOT NULL CHECK (estado IN (
        'PENDIENTE_VERIFICACION', 'ACTIVA', 'ELIMINADA'
    )),
    celular_verificado_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_usuario_celular UNIQUE (celular),
    CONSTRAINT ck_usuario_celular_e164 CHECK (celular ~ '^[+][1-9][0-9]{7,14}$'),
    CONSTRAINT ck_usuario_estado_verificacion CHECK (
        (estado = 'ACTIVA' AND (rol = 'ADMINISTRADOR' OR celular_verificado_at IS NOT NULL))
        OR estado <> 'ACTIVA'
    )
);

CREATE UNIQUE INDEX uq_usuario_correo_normalizado
    ON usuario (LOWER(correo))
    WHERE correo IS NOT NULL;

CREATE UNIQUE INDEX uq_usuario_administrador
    ON usuario (rol)
    WHERE rol = 'ADMINISTRADOR';

CREATE TABLE bootstrap_state (
    name VARCHAR(64) PRIMARY KEY,
    completed_at TIMESTAMPTZ
);

CREATE TABLE sesion (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    device_id VARCHAR(128) NOT NULL,
    session_version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT ck_sesion_expiry CHECK (expires_at > created_at)
);

CREATE INDEX ix_sesion_usuario_activa
    ON sesion (usuario_id, expires_at)
    WHERE revoked_at IS NULL;

CREATE TABLE ubicacion (
    id UUID PRIMARY KEY,
    latitud DOUBLE PRECISION NOT NULL CHECK (latitud BETWEEN -90 AND 90),
    longitud DOUBLE PRECISION NOT NULL CHECK (longitud BETWEEN -180 AND 180),
    direccion VARCHAR(300) NOT NULL,
    referencia VARCHAR(300),
    punto GEOGRAPHY(Point, 4326) GENERATED ALWAYS AS (
        ST_SetSRID(ST_MakePoint(longitud, latitud), 4326)::geography
    ) STORED,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_ubicacion_punto ON ubicacion USING GIST (punto);

ALTER TABLE usuario
    ADD COLUMN ubicacion_id UUID UNIQUE REFERENCES ubicacion(id);

CREATE TABLE dispositivo_token (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    token TEXT NOT NULL,
    plataforma VARCHAR(16) NOT NULL CHECK (plataforma IN ('ANDROID', 'IOS')),
    ultima_conexion_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_dispositivo_token_usuario_token UNIQUE (usuario_id, token)
);

CREATE INDEX ix_dispositivo_token_usuario ON dispositivo_token (usuario_id);
