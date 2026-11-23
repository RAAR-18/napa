-- RF-70: el código de verificación también puede enviarse por correo.
-- El CHECK de V5 se declaró en la columna, así que se busca por definición y no por nombre.
DO $$
DECLARE
    restriccion TEXT;
BEGIN
    FOR restriccion IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'mensaje'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%canal%'
    LOOP
        EXECUTE format('ALTER TABLE mensaje DROP CONSTRAINT %I', restriccion);
    END LOOP;
END $$;

ALTER TABLE mensaje
    ADD CONSTRAINT mensaje_canal_check CHECK (canal IN ('WHATSAPP', 'SMS', 'CORREO'));
