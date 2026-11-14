-- RF-49: registro de cada contacto del cliente con el vendedor o el domiciliario (sin contenido de la conversación).
CREATE TABLE canal_contacto (
    id UUID PRIMARY KEY,
    pedido_id UUID NOT NULL REFERENCES pedido(id),
    cliente_id UUID NOT NULL REFERENCES usuario(id),
    destinatario_id UUID NOT NULL REFERENCES usuario(id),
    destinatario VARCHAR(16) NOT NULL CHECK (destinatario IN ('VENDEDOR', 'DOMICILIARIO')),
    tipo VARCHAR(16) NOT NULL CHECK (tipo IN ('MENSAJE', 'LLAMADA')),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_canal_contacto_pedido ON canal_contacto (pedido_id, created_at);
