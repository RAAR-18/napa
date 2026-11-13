# Catálogo de eventos de dominio

**Updated**: 2026-10-05
**Relacionado**: [`spec-general.md`](spec-general.md), `RF-38` (notificaciones), `specs/foundations/plan.md` (outbox).

## Reglas

1. Un evento es un **hecho ya ocurrido**, en pasado (`PedidoCreadoEvent`), inmutable (`record` de Java 21) y vive en `<modulo>/domain/event/`.
2. Lo emite el **caso de uso** (por el puerto `EventPublisherPort`) dentro de la misma transacción que el cambio, y se guarda en `outbox_event`.
3. Lo consumen **adaptadores de entrada** `adapters/in/event/` del módulo interesado. El listener no tiene lógica: llama a un puerto de entrada.
4. **El módulo que emite no conoce ni planifica a sus listeners.** Los listeners se planifican en el plan del módulo que reacciona (p. ej. notificaciones en el plan de RF-38).
5. Eventos para **reaccionar**; invariantes (stock, pago confirmado, un domicilio activo) con puertos síncronos dentro de la transacción.
6. Los manejadores son **idempotentes** (se entrega al menos una vez): se deduplica por `eventId`.
7. El payload lleva identificadores y datos mínimos, sin datos personales (RNF04). El destinatario consulta lo demás.
8. Excepción: la **verificación del celular** (RF-70) no usa eventos ni outbox: es una llamada directa al puerto `VerificarCelularPort`; si se pierde, el usuario pulsa "reenviar".

## Sobre (envelope)

```json
{
  "eventId": "uuid",
  "type": "domicilios.domicilio-publicado",
  "version": 1,
  "occurredAt": "2026-10-05T14:00:00Z",
  "aggregateType": "Domicilio",
  "aggregateId": "uuid",
  "actorUserId": "uuid",
  "payload": {}
}
```

## Catálogo

| # | `type` (Java) | Lo emite (RF · caso de uso) | Cuándo | Payload | Reacciones (módulo → puerto) |
|---|---|---|---|---|---|
| E01 | `pedidos.pedido-creado` (`PedidoCreadoEvent`) | RF-45 · `RealizarPedidoUseCase` | Se registra el pedido en `PENDIENTE` | `pedidoId`, `compradorId`, `vendedorId`, `modalidad`, `esReserva` | notificaciones → avisar al vendedor ("nuevo pedido") |
| E02 | `pedidos.pedido-aceptado` (`PedidoAceptadoEvent`) | RF-53 · `ResponderPedidoUseCase` | El vendedor acepta | `pedidoId`, `compradorId`, `modalidad`, `requierePagoDigital`, `total` | notificaciones → avisar al comprador (en domicilio, que debe transferir el total) |
| E03 | `pedidos.pedido-rechazado` (`PedidoRechazadoEvent`) | RF-53 | El vendedor rechaza | `pedidoId`, `compradorId` | notificaciones → avisar al comprador |
| E04 | `pagos.pago-pedido-confirmado` (`PagoPedidoConfirmadoEvent`) | RF-42 · `ConfirmarPagoPedidoUseCase` | El vendedor confirma el cobro | `pagoId`, `pedidoId`, `compradorId`, `medio`, `monto` | notificaciones → avisar al comprador |
| E05 | `entregas.entrega-directa-iniciada` (`EntregaDirectaIniciadaEvent`) | RF-36 | El ambulante inicia la entrega | `pedidoId`, `compradorId` | notificaciones → avisar al comprador |
| E06 | `pedidos.pedido-listo-para-recoger` (`PedidoListoParaRecogerEvent`) | RF-64 | El vendedor marca listo | `pedidoId`, `compradorId` | notificaciones → avisar al comprador |
| E07 | `pedidos.pedido-entregado` (`PedidoEntregadoEvent`) | RF-37, RF-65 | Se confirma entrega o retiro y cobro | `pedidoId`, `compradorId`, `vendedorId` | calificaciones → habilitar calificación de la interacción (RF-01) |
| E08 | `domicilios.domicilio-publicado` (`DomicilioPublicadoEvent`) | RF-14 · `PublicarDomicilioUseCase` | Se crea el domicilio `DISPONIBLE` | `domicilioId`, `pedidoId`, `vendedorId`, `tarifa` | notificaciones → avisar a los domiciliarios |
| E09 | `domicilios.domicilio-asignado` (`DomicilioAsignadoEvent`) | RF-19 | Un domiciliario lo acepta | `domicilioId`, `domiciliarioId`, `compradorId`, `vendedorId` | notificaciones → avisar al comprador y al vendedor |
| E10 | `domicilios.domicilio-recogido` (`DomicilioRecogidoEvent`) | RF-23 | El domiciliario confirma la recogida | `domicilioId`, `vendedorId` | notificaciones → avisar al vendedor |
| E11 | `domicilios.domicilio-en-camino` (`DomicilioEnCaminoEvent`) | RF-23 | El vendedor confirma la salida | `domicilioId`, `compradorId` | notificaciones → avisar al comprador |
| E12 | `domicilios.domicilio-entregado` (`DomicilioEntregadoEvent`) | RF-24 | El domiciliario confirma con el código | `domicilioId`, `compradorId`, `vendedorId`, `domiciliarioId` | notificaciones → comprador (confirmar llegada) y vendedor (pagar tarifa) |
| E13 | `domicilios.domicilio-finalizado` (`DomicilioFinalizadoEvent`) | RF-25 | El comprador confirma la llegada | `domicilioId`, `compradorId`, `vendedorId`, `domiciliarioId` | notificaciones → domiciliario y vendedor; calificaciones → habilitar |
| E14 | `pagos.pago-tarifa-registrado` (`PagoTarifaRegistradoEvent`) | RF-43 | El vendedor registra el pago de la tarifa | `pagoId`, `domicilioId`, `domiciliarioId`, `monto` | notificaciones → avisar al domiciliario |
| E15 | `domicilios.domicilio-cancelado` (`DomicilioCanceladoEvent`) | RF-13 | El administrador cancela | `domicilioId`, `compradorId`, `vendedorId`, `domiciliarioId?` | notificaciones → avisar a las partes |
| E16 | `reportes.reporte-resuelto` (`ReporteResueltoEvent`) | RF-62 | El administrador cierra el reporte | `reporteId`, `autorId` | notificaciones → avisar al autor |
| E17 | `calificaciones.comentario-moderado` (`ComentarioModeradoEvent`) | RF-06 | El administrador edita o elimina | `calificacionId`, `autorId`, `accion` | notificaciones → avisar al autor |
