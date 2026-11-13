# specs/

Documentación de diseño de Ñapa siguiendo Spec-Driven Development: **primero la spec (QUÉ), luego el plan (CÓMO), luego el código**.

```text
specs/
├── README.md                      # este índice y el flujo de trabajo
├── templates/
│   ├── spec-template.md           # plantilla de las specs funcionales
│   └── plan-template.md           # plantilla de los planes técnicos
├── general/                       # QUÉ compartido por todos los RF
│   ├── spec-general.md            # roles, convenciones de API, modelo de datos (ER), estados
│   └── eventos.md                 # catálogo de eventos de dominio
├── foundations/
│   └── plan.md                    # CÓMO compartido: Auth, outbox/eventos, idempotencia, FCM, mapas, offline
└── RF-NN-nombre/                  # una carpeta por requerimiento funcional (67 en total)
    ├── spec.md                    # especificación funcional (fuente de verdad)
    └── plan.md                    # plan técnico (se crea cuando el RF entra a construcción)
```

## Flujo

1. **Spec** (`RF-NN-nombre/spec.md`): comportamiento, escenarios Given/When/Then, FR y SC. Sin decisiones técnicas.
2. **Plan** (`RF-NN-nombre/plan.md`): diseño, contratos, eventos emitidos, tareas y pruebas. Se escribe desde `templates/plan-template.md` y no puede contradecir la spec ni `general/`.
3. **Implementación**: solo cuando el *Plan Review Checklist* del plan está completo. Las tareas (`T0NN`) viven dentro del plan; si el plan supera unas 300 líneas se puede separar un `tasks.md` en la misma carpeta.
4. **Trazabilidad**: cualquier cambio de alcance se refleja en `requerimientos/funcionales.md`, `investigacion/HistoriasDeUsuario/historias_de_usuario.md` y `requerimientos/trazabilidad.md`.

## Reglas

- Si algo cambia de comportamiento, **se corrige primero la spec**, después el plan.
- El modelo de datos, las convenciones de API (cabeceras, códigos, filtros) y los eventos **no se redefinen en los planes**: se referencian en `general/`.
- Los listeners de eventos se planifican en el módulo que reacciona (p. ej. notificaciones, RF-38), no en el caso de uso que emite el evento.
- La numeración no se reutiliza: RF-20, RF-21 y RF-46 están retirados.
- Nombre de carpeta: `RF-NN-` + título en minúsculas sin tildes, igual al de `requerimientos/trazabilidad.md`.

## Orden de lectura para un RF nuevo

`general/spec-general.md` → `general/eventos.md` → `RF-NN/spec.md` → `foundations/plan.md` → `RF-NN/plan.md`.

## Orden de construcción

Ver "Orden de planes por RF" en [`foundations/plan.md`](foundations/plan.md).