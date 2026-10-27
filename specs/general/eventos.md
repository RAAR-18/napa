# Catálogo de eventos de dominio

**Updated**: 2026-10-14
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
9. La numeración no se reutiliza: **E13 está retirado** (ya no existe la finalización del domicilio por el cliente).

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
| E01 | `pedidos.pedido-creado` (`PedidoCreadoEvent`) | RF-45 · `RealizarPedidoUseCase` | Se registra el pedido en `PENDIENTE` | `pedidoId`, `compradorId`, `vendedorId`, `modalidad`, `reserva?` | notificaciones → avisar al vendedor ("nuevo pedido") |
| E02 | `pedidos.pedido-aceptado` (`PedidoAceptadoEvent`) | RF-53 · `ResponderPedidoUseCase` | El vendedor acepta | `pedidoId`, `compradorId`, `modalidad`, `requierePagoDigital`, `total` | notificaciones → avisar al comprador (en domicilio, que debe transferir el total) |
| E03 | `pedidos.pedido-rechazado` (`PedidoRechazadoEvent`) | RF-53 | El vendedor rechaza | `pedidoId`, `compradorId` | notificaciones → avisar al comprador |
| E04 | `pagos.pago-pedido-confirmado` (`PagoPedidoConfirmadoEvent`) | RF-42 · `ConfirmarPagoPedidoUseCase` | El vendedor confirma el cobro | `pagoId`, `pedidoId`, `compradorId`, `medio`, `monto`, `modalidad` | notificaciones → avisar al comprador solo si modalidad es DOMICILIO (en entrega directa y recogida el aviso es el de E07) |
| E05 | `entregas.entrega-directa-iniciada` (`EntregaDirectaIniciadaEvent`) | RF-36 | El ambulante inicia la entrega | `pedidoId`, `compradorId` | notificaciones → avisar al comprador |
| E06 | `pedidos.pedido-listo-para-recoger` (`PedidoListoParaRecogerEvent`) | RF-64 · `TransicionarPedidoUseCase` (módulo `pedidos`) | El vendedor marca listo | `pedidoId`, `compradorId` | notificaciones → avisar al comprador |
| E07 | `pedidos.pedido-entregado` (`PedidoEntregadoEvent`) | RF-37, RF-65 y RF-24 · `TransicionarPedidoUseCase` (módulo `pedidos`) | Se confirma entrega o retiro y cobro; en domicilio, cuando el domiciliario valida el código | `pedidoId`, `compradorId`, `vendedorId`, `domicilioId?` | notificaciones → avisar al comprador de la entrega con invitación a calificar, solo si `domicilioId` es nulo (en domicilio lo hace E12). calificaciones: la elegibilidad se deriva del estado del pedido, sin listener |
| E08 | `domicilios.domicilio-publicado` (`DomicilioPublicadoEvent`) | RF-14 · `PublicarDomicilioUseCase` | Se crea el domicilio `DISPONIBLE` | `domicilioId`, `pedidoId`, `vendedorId`, `tarifa` | notificaciones → avisar a todos los domiciliarios activos |
| E09 | `domicilios.domicilio-asignado` (`DomicilioAsignadoEvent`) | RF-19 | Un domiciliario lo acepta | `domicilioId`, `domiciliarioId`, `compradorId`, `vendedorId` | notificaciones → avisar al comprador y al vendedor |
| E10 | `domicilios.domicilio-recogido` (`DomicilioRecogidoEvent`) | RF-23 | El domiciliario confirma la recogida | `domicilioId`, `vendedorId` | notificaciones → avisar al vendedor |
| E11 | `domicilios.domicilio-en-camino` (`DomicilioEnCaminoEvent`) | RF-23 | El vendedor confirma la salida | `domicilioId`, `compradorId` | notificaciones → avisar al comprador |
| E12 | `domicilios.domicilio-entregado` (`DomicilioEntregadoEvent`) | RF-24 | El domiciliario ingresa el código correcto del cliente | `domicilioId`, `pedidoId`, `compradorId`, `vendedorId`, `domiciliarioId` | notificaciones → vendedor (pagar la tarifa, RF-43) y comprador (aviso de entrega); el texto del aviso al comprador incluye la invitación a calificar |
| E14 | `pagos.pago-tarifa-registrado` (`PagoTarifaRegistradoEvent`) | RF-43 | El vendedor registra el pago de la tarifa | `pagoId`, `domicilioId`, `domiciliarioId`, `monto` | notificaciones → avisar al domiciliario |
| E15 | `domicilios.domicilio-cancelado` (`DomicilioCanceladoEvent`) | RF-13 | El administrador cancela (el pedido pasa a `CANCELADO` en la misma transacción) | `domicilioId`, `pedidoId`, `compradorId`, `vendedorId`, `domiciliarioId?` | notificaciones → avisar a las partes |
| E16 | `reportes.reporte-resuelto` (`ReporteResueltoEvent`) | RF-62 | El administrador cierra el reporte | `reporteId`, `autorId` | notificaciones → avisar al autor |
| E17 | `calificaciones.comentario-moderado` (`ComentarioModeradoEvent`) | RF-06 | El administrador edita o elimina | `calificacionId`, `autorId`, `accion`, `motivo` | notificaciones → avisar al autor de lo que se hizo y por qué |
| E18 | `reportes.reporte-creado` (`ReporteCreadoEvent`) | RF-60 · `CrearReporteUseCase` | Un usuario crea un reporte | `reporteId`, `autorId` | notificaciones → avisar a todos los administradores con cuenta ACTIVA |