# Implementation Plan: Configuración de tarifa mínima de domicilio

**Date**: 2026-11-11
**Spec**: [RF-44-configuracion-de-tarifa-minima-de-domicilio.md](./RF-44-configuracion-de-tarifa-minima-de-domicilio.md) (fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (modelo, API y cálculo de tarifa)
**Requerimiento funcional**: RF-44
**Historias de usuario**: HU-71
**Prioridad (la mayor de la spec)**: P3; su parámetro por defecto habilita RF-45 P1
**Depende de**: RF-10 (rol administrador), foundations (PostgreSQL, migraciones)
**Habilita a**: RF-45

> Este plan describe la implementación de la spec; no cambia su comportamiento.

## Summary

La tarifa mínima inicial se configura mediante `napa.tarifa.minima-defecto`, con valor predeterminado de **3050 COP**, acordado por el equipo el 2026-11-11. `GET /api/v1/tarifa-minima` devuelve la última tarifa administrada o el valor configurado cuando aún no hay cambios. `POST /api/v1/tarifa-minima` registra cada cambio efectivo como una fila histórica y solo está disponible para `ADMINISTRADOR`. Los pedidos guardan su tarifa al crearse; los cambios posteriores no modifican pedidos existentes.

## Estado actual del código

El backend de RF-44 ya implementa dominio, puertos, lectura por defecto, actualización administrativa, historial, endpoints, autorización, migración V9 y serialización de cambios concurrentes. Pasaron las pruebas unitarias, ArchUnit y la integración HTTP con PostgreSQL/Redis. RF-45 aún no consume el puerto de consulta; su plan y número de migración se actualizaron para depender de RF-44. El archivo anterior de este plan contenía por error el plan de RF-43; el plan correcto de RF-43 se conserva en su propia carpeta.

## Contrato y decisiones

- `GET /api/v1/tarifa-minima`: solo administrador; `200` con `{ "valor": 3050, "createdAt": null }` antes del primer cambio y la fecha UTC del último cambio después.
- `POST /api/v1/tarifa-minima`: solo administrador, exige `Idempotency-Key`; cuerpo `{ "valor": <entero COP positivo> }`; devuelve el valor vigente y su fecha. Un valor igual al vigente es un no-op y no agrega una fila de historial.
- Dinero se representa como entero `long` en pesos colombianos. Valores cero, negativos, decimales o fuera de rango se rechazan con `400 VALIDATION_ERROR`.
- Historial append-only con `id`, `valor`, `created_at` y `administrador_id`. La fila previa define el valor anterior; no se almacena una copia redundante.
- Las escrituras se serializan dentro de la transacción con un advisory lock de PostgreSQL, para que actualizaciones administrativas concurrentes produzcan un orden inequívoco. La lectura usa la fila de mayor `id`.
- Sin evento de dominio: la configuración no requiere notificación y la escritura del historial es la auditoría definida por la spec.
- La migración RF-44 usa V9; por dependencia, la migración de pedidos prevista en RF-45 pasa a V10.

## Spec Coverage

| Spec | Diseño | Pruebas |
|---|---|---|
| FR-001 | `POST /tarifa-minima`, puerto de entrada y caso de uso de administración | Éxito y rol no autorizado |
| FR-002 | Validación en DTO, value object y restricción `CHECK (valor > 0)` | Cero, negativo y valor válido |
| FR-003 | RF-45 consulta el puerto de tarifa vigente antes de calcular | Prueba del puerto y del cálculo con tarifa menor al piso |
| FR-004 | El cambio solo agrega historial; el pedido congela `tarifa_domicilio` | El valor guardado de pedidos existentes no cambia |
| FR-005 | Tabla append-only ordenada por `id`, fecha UTC y administrador | Dos cambios producen dos filas y orden |
| FR-006 | Propiedad validada con valor predeterminado 3050 COP, usada cuando no hay historial | Contexto con valor por defecto y lectura sin cambios |
| SC-001 | Flujo de una llamada | Prueba HTTP del endpoint |
| SC-002 / SC-003 | Lectura transaccional del vigente y uso al cotizar/crear pedidos | Pruebas de integración RF-45 con valor recién actualizado |
| US1-E1–E3 | Actualización, piso y conservación de tarifas históricas | Pruebas HTTP, persistencia y pedido |

## Modelo y API

La tabla sigue la entidad transcrita en `specs/general/spec-general.md`:

```text
tarifa_minima(id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
              valor BIGINT NOT NULL CHECK (valor > 0),
              created_at TIMESTAMPTZ NOT NULL,
              administrador_id UUID NOT NULL REFERENCES usuario(id))
```

La tabla no se siembra con el valor por defecto: mientras está vacía, la configuración es la fuente de verdad. Esto permite desplegar el parámetro sin introducir una fila que parezca un cambio administrativo. Una actualización igual al valor efectivo no crea fila. `GET` no expone el historial completo porque el contrato general define únicamente el valor vigente.

## Tareas

### Fase 1: base

- [x] T001 Agregar `napa.tarifa.minima-defecto` con valor 3050, binding tipado y validación positiva al inicio.
- [x] T002 Crear `V9__tarifa_minima.sql` con tabla, FK y `CHECK`; la clave primaria respalda la consulta de la última fila.
- [x] T003 Crear `TarifaMinima` (validación positiva), puerto de consulta/cambio y adaptador JDBC; el cambio toma advisory lock dentro de la transacción.
- [x] T004 Agregar regla ArchUnit para que el adaptador web dependa del puerto de entrada, no de la implementación.

### Fase 2: consulta y actualización administrativa

- [x] T010 Escribir pruebas unitarias del dominio y del caso de uso: default, valor histórico, valor inválido, no-op e inserción de cambio.
- [x] T011 Escribir pruebas HTTP con PostgreSQL/Testcontainers: GET con y sin historial, POST, rol admin, usuario no admin, Idempotency-Key y campos inválidos.
- [x] T012 Implementar puertos `ConsultarTarifaMinimaPort` y `ActualizarTarifaMinimaPort` y sus casos de uso transaccionales.
- [x] T013 Implementar `TarifaMinimaController`, validación de query/body, `Cache-Control: no-store` y respuestas según el contrato.
- [x] T014 Verificar serialización concurrente de cambios y secuencia histórica en una prueba de integración PostgreSQL.

### Fase 3: integración de RF-45

- [x] T020 Actualizar el plan RF-45 para depender de `ConsultarTarifaMinimaPort`, reservar V10 para RF-63 y asignar V11 a pedidos, y alinear la ruta externa con la decisión OpenRouteService de foundations.
- [ ] T021 Implementar en RF-45 el adaptador al puerto de tarifa y calcular con el valor vigente dentro del flujo de cotización/creación; congelar el resultado en el pedido.
- [ ] T022 Probar que una actualización modifica el piso de nuevos pedidos y no cambia pedidos ya creados.

## Estrategia de pruebas

- Unitarias: validación positiva, valor por defecto, no-op e historial.
- Integración PostgreSQL: migración V9, lectura fallback, escrituras serializadas y FK al administrador.
- HTTP: autorización, contrato, validaciones y no-store.
- Integración con RF-45: piso aplicado al cálculo de domicilio y tarifa congelada.
- Regresión: suites backend, ArchUnit y pruebas existentes.

## Plan Review Checklist

- [x] La spec RF-44 y el contrato general cubren las rutas, roles y comportamiento.
- [x] Valor inicial acordado: 3050 COP.
- [x] El orden de despliegue V9 (tarifa) → V10 (pedido) queda explícito.
- [x] Cambios administrativos concurrentes serializados en PostgreSQL.
- [x] Valor por defecto separado del historial y valor del pedido congelado.
- [x] La estrategia de pruebas usa JUnit, ArchUnit y Testcontainers existentes.

## Fuera de alcance

- Pantalla administrativa móvil/web: el contrato funcional no requiere una UI y el backend permite configurar la tarifa desde un cliente administrativo futuro.
- Modificar tarifas de pedidos existentes o publicar historial en un endpoint.
- Cambiar la fórmula de tarifa, proveedor de rutas o precisión; rigen RF-45 y `spec-general.md`.
