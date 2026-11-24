ALTER TABLE emprendimiento
    ADD COLUMN categoria_personalizada VARCHAR(80);

ALTER TABLE emprendimiento
    ADD CONSTRAINT ck_emprendimiento_categoria_personalizada
    CHECK (categoria_personalizada IS NULL OR (
        categoria = 'OTROS'
        AND length(btrim(categoria_personalizada)) BETWEEN 1 AND 80
        AND categoria_personalizada = btrim(categoria_personalizada)
    ));
