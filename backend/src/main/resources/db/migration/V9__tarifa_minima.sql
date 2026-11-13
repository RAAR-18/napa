CREATE TABLE tarifa_minima (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    valor BIGINT NOT NULL CHECK (valor > 0),
    created_at TIMESTAMPTZ NOT NULL,
    administrador_id UUID NOT NULL REFERENCES usuario(id)
);
