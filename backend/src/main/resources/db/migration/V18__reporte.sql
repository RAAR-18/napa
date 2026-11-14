-- RF-60 a RF-62: reportes de problemas para el administrador.
CREATE TABLE reporte (
    id UUID PRIMARY KEY,
    autor_id UUID NOT NULL REFERENCES usuario(id),
    motivo VARCHAR(24) NOT NULL CHECK (motivo IN (
        'PEDIDO', 'DOMICILIO', 'PAGO', 'COMPORTAMIENTO_USUARIO', 'PLATAFORMA', 'OTRO'
    )),
    descripcion VARCHAR(500) NOT NULL CHECK (length(btrim(descripcion)) BETWEEN 10 AND 500),
    pedido_id UUID REFERENCES pedido(id),
    domicilio_id UUID REFERENCES domicilio(id),
    usuario_reportado_id UUID REFERENCES usuario(id),
    estado VARCHAR(16) NOT NULL CHECK (estado IN ('PENDIENTE', 'EN_REVISION', 'RESUELTO')),
    nota_resolucion VARCHAR(1000),
    resuelto_por_id UUID REFERENCES usuario(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_reporte_una_entidad CHECK (
        num_nonnulls(pedido_id, domicilio_id, usuario_reportado_id) <= 1
    ),
    CONSTRAINT ck_reporte_resolucion CHECK (
        (estado = 'RESUELTO') = (nota_resolucion IS NOT NULL AND resuelto_por_id IS NOT NULL)
    )
);

CREATE INDEX ix_reporte_estado_created_at ON reporte (estado, created_at, id);
CREATE INDEX ix_reporte_autor_created_at ON reporte (autor_id, created_at DESC);
