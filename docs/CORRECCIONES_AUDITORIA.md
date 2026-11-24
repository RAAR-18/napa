# Seguimiento de correcciones de auditoría

Fecha: 24 de noviembre de 2026. Base revisada: `35cc46d`. Rama: `fix/p0-01-outbox-web`.

El informe aportado es un diagnóstico; sus instrucciones y propuestas no sustituyen las decisiones funcionales ni las especificaciones del repositorio.

## Entrega actual

| Incidencia | Cambio | Verificación y límite |
|---|---|---|
| P0-01: error VFS en cola web | Variante IndexedDB, SQLite nativo conservado, apertura con mensaje claro y reintento | Pruebas de persistencia entre instancias, concurrencia, separación por cuenta, corrección condicional y rollback. Falta prueba manual en navegador con recarga y reconexión. |
| P0-03: entrada de horarios | `TimeField` compartido en creación/edición, formato HH:mm y rechazo de valores inválidos | Pruebas de componente y pantallas. No cambia el contrato ni la admisión de horarios nocturnos. |

La prueba de inventario offline que había agotado tiempo pasó al repetir la suite de productos (29 pruebas). No se alteró su timeout para ocultar el problema.

Validación automatizada: suite frontend completa, 293 pruebas en 43 suites, sin fallos; typecheck y lint correctos. `fake-indexeddb` se añadió exclusivamente como dependencia de pruebas, sin cambiar versiones declaradas de Expo/React Native. No se modificó backend ni se ejecutaron migraciones.

## Precauciones de despliegue de P0-01

El adaptador web no migra acciones previas de SQLite ni cambia otras cachés que aún usan SQLite. Sincronizar o recuperar pendientes antiguos antes de desplegar y no borrar los datos del sitio. Las pruebas usan IndexedDB simulado; el recorrido real debe probarse con el navegador y la API.

## Siguiente incidencia identificada

P0-06: `PedidoCatalogo.tsx` recupera borradores, pero guarda uno solo ante una acción explícita offline. Falta guardado automático al cambiar el carrito antes de navegar. Revisar `pedido-offline.ts`, su SQLite en web, el aislamiento por cuenta, las carreras entre recuperación y escritura y la limpieza tras confirmar o encolar un pedido. No darla por corregida por el mero cambio de cola.

## Pendientes por contrastar

- P0-02/P1-04: el plan RF-45 fija 20 productos/20 unidades y las reglas de inventario; no retirar límites ni reservar stock al crear solo porque lo propone el informe. Verificar y corregir fallos de validación sin cambiar el acuerdo inadvertidamente.
- P0-04: conservar la política vigente hasta contrastar el informe con las decisiones de autenticación; no reducir a seis caracteres ni exigir composición sin revisar su justificación.
- P0-05 y restantes incidencias: verificar sobre el código actual antes de aplicar propuestas.
- Selectores de mapa, navegación y recorrido de varias entregas: pospuestos por instrucción del usuario hasta avanzar con las correcciones del informe.

Git: no se hicieron commits, push ni cambios de rama. Había dos archivos nuevos parcialmente preparados por el usuario; se conservó su selección. Debe volver a revisar los archivos antes de preparar el commit final.

Validación adicional: exportación web de Expo completada correctamente en una carpeta temporal. Se comprobó que el bundle contiene `napa-outbox-web`, correspondiente al adaptador IndexedDB. Esto no sustituye la prueba manual de publicación/recarga/reconexión.

## Segundo bloque aplicado

| Incidencia | Resultado de implementación |
|---|---|
| P0-06 | Guardado automático del carrito por cuenta/negocio y restauración tras navegar; limpieza coordinada tras enviar. |
| P1-03 | Borrador conserva dirección y referencia; se propone la última dirección cotizada del cliente. Persistencia local por cuenta, no cambio del perfil servidor. |
| P1-06 | Acceso a editar negocio desde Mis productos y guía a edición ante conflicto de creación. |
| P2-03 | Se conserva menú móvil y se retira la barra inferior que duplicaba destinos. |
| P2-04 | Confirmación de métodos de pago con aviso verde de éxito; la información de disponibilidad mantiene su estilo informativo. |
| P2-05 | El botón de calificación depende de las opciones aún pendientes y se actualiza al volver a la pantalla. |
| P3-01 | Texto Crear cuenta unificado en entrada, login y título de registro. |
| P3-02 | Un único acceso Mi cuenta en navegación; el perfil se conserva dentro de la cuenta. |
| P0-02, parcial | Validación de disponibilidad conocida antes de cotizar y rechazo de coordenadas vacías. No cambia la política de stock al aceptar. |
| P0-04, parcial | Indicadores de longitud y coincidencia de contraseña y bloqueo de registro cuando no coinciden. Se conserva la política D14 (8–128, sin composición obligatoria). |

El almacenamiento web de borradores/cotizaciones se separó de SQLite para que la persistencia del carrito no dependa del VFS web. Otros cachés SQLite ajenos a este módulo continúan sin cambios. Los límites RF-45 y la política D14 se mantienen; retirarlos o sustituirlos cambia el alcance vigente.

Pendientes del informe: P0-05 (proveedores de verificación), P1-01 (pagos de reservas, contrastar reglas actuales), P1-02 (tiempo real), P1-04 (propuesta de retirar límites), P1-05 (ubicación periódica), P1-07 (selector visual de fecha), P2-01 (categoría personalizada), P2-02 (formulario ambulante). P0-02 y P0-04 conservan las salvedades descritas. El tester del grupo valida funcionalmente el conjunto.

## Validación del segundo bloque

- Comprobación general: 41 suites pasaron; dos pruebas esperaban el borrador y la barra móvil anteriores y se adaptaron a la nueva interfaz/datos.
- Repetición dirigida final: 5 suites, 55 pruebas correctas, incluyendo borrador automático/restauración, IndexedDB, navegación y registro. Las demás suites no se repitieron porque sus archivos no cambiaron después de la comprobación general.
- Typecheck y lint finales correctos, sin advertencias de lint. Exportación web final de Expo correcta (1.058 módulos), en directorio temporal fuera del repositorio.
- No hubo cambios backend ni necesidad de Docker en esta entrega. El tester verifica los recorridos funcionales; no se bloquea el siguiente bloque por esa validación.

## Tercer bloque aplicado y revisión de Git

- Git: al revisar el pedido de comprobar el commit, HEAD seguía en `35cc46d`. Los cambios anteriores estaban preparados para commit, pero no existía un commit nuevo. Nombre y correo de autor están configurados; no se atribuyó el problema a falta de identidad sin evidencia. No se modificó el índice ni se creó commit/push.
- P0-05, parcial: correo recomendado cuando la cuenta tiene uno; SMS alternativo; protección contra envío/confirmación simultáneos. Se conserva el correo opcional y no se envían mensajes automáticamente. La configuración y entrega de proveedores externos sigue pendiente del entorno.
- P1-02, actualización de interfaz: notificaciones y pedidos recibidos se refrescan cada 30 segundos mientras su pantalla está visible y sin otra carga en curso. No se añadió SSE/WebSocket ni se prometen avisos con la app cerrada.
- P1-07: selector visual del sistema en Android/iOS y control del navegador en web. La fecha de reserva deja de introducirse como texto ISO.
- P1-01 revisado: RF-42 FR-002 dispone cobro al entregar/retirar en entrega directa y recogida, incluidas reservas; el anticipo se confirma en domicilio. El endpoint restringido a domicilio coincide con la regla vigente, no constituye por sí solo un fallo. Un botón del cliente «Ya pagué» o el anticipo de toda reserva cambiarían el alcance y no se implementaron automáticamente.
- P1-04 revisado: los límites de 20 ítems/20 unidades son una decisión explícita del plan RF-45. No se retiraron silenciosamente.
- P2-02 revisado: RF-28 FR-005 permite zona aproximada y referencia al ambulante sin dirección fija; el formulario ya distingue la dirección por rol. Quitar toda coordenada implica cambiar el alcance; la futura selección visual en mapa sigue pospuesta.
- P2-01: categoría personalizada requiere ampliar la lista fija de RF-28 y el modelo; permanece como propuesta funcional pendiente.

Validación del tercer bloque: las pruebas afectadas del primer pase tuvieron 67 aciertos y un fallo en la configuración del test de refresco; tras corregirlo se repitieron únicamente los cuatro archivos afectados: 22 pruebas correctas. Las pruebas de compra y pedidos del primer pase ya habían pasado. Typecheck/lint finales y exportación web correctos. No se modificó backend ni se necesitó Docker.

Dependencia nativa nueva: `@react-native-community/datetimepicker` 9.2.1. Para usarla en un build de desarrollo propio de Android/iOS hay que regenerar ese build una vez. El tester del grupo valida el recorrido funcional y el dispositivo real.
