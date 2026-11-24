# Seguimiento de correcciones de auditoría — Ñapa

Actualizado: 24 de noviembre de 2026. Base del bloque actual: `a561f42`, rama `fix/p0-01-outbox-web`.

## Estado vigente

| Incidencia | Resultado |
|---|---|
| P0-01 | Cola web en IndexedDB; SQLite conservado en Android/iOS. Errores claros, persistencia y concurrencia verificadas. No migra pendientes antiguos ni sustituye todas las cachés SQLite. |
| P0-02 | Validación de disponibilidad conocida antes de cotizar. El backend valida y descuenta al aceptar; no se reserva inventario al crear, según RF-45. |
| P0-03 | Entrada de horario compartida, numérica, HH:mm, validación de rango y de hora incompleta. |
| P0-04 | Feedback de longitud/coincidencia y bloqueo ante contraseñas distintas. Se conserva D14: 8–128 caracteres, sin composición obligatoria. |
| P0-05 | Correo recomendado si está registrado, SMS alternativo y bloqueo de envío/confirmación simultáneos. La entrega real depende de la configuración de los proveedores. |
| P0-06 | Guardado automático del carrito por cuenta/negocio, restauración y limpieza coordinada tras enviar. |
| P1-01 | Revisado contra RF-42 FR-002: directa/recogida cobran al entregar/retirar, también en reservas; domicilio permite anticipo. No se cambia esta regla solo por el informe. |
| P1-02 | Refresco de notificaciones/pedidos recibidos cada 30 s mientras su pantalla está visible. No es SSE/WebSocket ni promete avisos con la app cerrada; push conserva su configuración. |
| P1-03 | Borrador conserva dirección/referencia y se propone la última dirección cotizada por cuenta. No modifica el perfil del servidor. |
| P1-04 | El límite de 20 ítems/20 unidades es un acuerdo explícito de RF-45. No se elimina silenciosamente. |
| P1-05 | Implementada actualización automática opcional del ambulante cada 2 min en primer plano y a partir de 100 m de desplazamiento. No incluye seguimiento en segundo plano. |
| P1-06 | Acceso a editar negocio desde Mis productos y guía a edición ante conflicto de creación. |
| P1-07 | Selector visual de reserva del sistema en móvil y del navegador en web; validación de fecha real y futura. |
| P2-01 | Otros admite texto personalizado opcional de hasta 80 caracteres, persistido y mostrado en creación, consulta propia, listado y detalle. Migración nueva V21. |
| P2-02 | El formulario ya distingue la dirección fija obligatoria de la zona ambulante con referencia/dirección opcional (RF-28 FR-005). Quitar toda coordenada cambia el alcance; mapa visual pospuesto. |
| P2-03 | Menú móvil único; se retiró la barra inferior que duplicaba destinos. |
| P2-04 | Confirmación de métodos de pago en un aviso de éxito; disponibilidad de domicilio conserva su estilo informativo. |
| P2-05 | Botón de calificación según opciones pendientes; se actualiza al volver a la pantalla. |
| P3-01 | Crear cuenta unificado en entrada, login y título de registro. |
| P3-02 | Un único acceso Mi cuenta en navegación; perfil conservado dentro de la cuenta. |

## Bloque actual: categoría y actualización de zona

La categoría sigue siendo un enum fijo. `categoriaPersonalizada` complementa únicamente OTROS; un texto vacío se normaliza a ausencia, más de 80 caracteres o combinado con otra categoría se rechaza. El nombre/categoría siguen siendo inmutables tras crear. Las ediciones y eliminaciones conservan el complemento.

V21 agrega una columna nullable y una restricción coherente en PostgreSQL, sin reescribir V1–V20 ni cambiar datos existentes. Las pruebas usan PostgreSQL/PostGIS de Testcontainers. No se ejecutó la migración en la base local del usuario; Flyway la aplicará al iniciar una API con este código.

La ubicación automática requiere activación expresa en Mi emprendimiento y permiso de ubicación. La preferencia pertenece a cuenta y negocio. No lee GPS sin una preferencia válida para la cuenta actual ni en segundo plano; conserva dirección/referencia y solo envía el punto. Las operaciones comprueban generación de sesión, propietario/rol y preferencia vigente. Sin conexión no se encolan posiciones antiguas.

## Comprobaciones y límites

El bloque anterior pasó comprobaciones de tipos, lint, pruebas afectadas y exportación web. En este bloque pasó la comprobación inicial del módulo de emprendimientos y ArchUnit, cargando V21 en PostgreSQL aislado, sin omisiones. Regresiones finales nuevas: 11 pruebas backend (dominio y persistencia), sin fallos, errores ni omisiones; frontend: 12 pruebas de la repetición dirigida final correctas, además de las consultas/pantallas que ya habían pasado. Typecheck, lint y exportación web finales correctos. La prueba que agotó 5 s pasó aislada sin cambiar su timeout. No se ejecutó de nuevo toda la suite porque el resto no cambió.

El tester del grupo valida los recorridos funcionales. Las comprobaciones se ejecutan por bloques y solo se repiten las afectadas por cambios/fallos. El mapa y el recorrido de varias entregas continúan pospuestos.

El selector de reservas incorporó `@react-native-community/datetimepicker` 9.2.1 en el bloque anterior: los builds de desarrollo Android/iOS deben regenerarse para incluirlo. Este bloque no añade dependencias nativas.

## Pendientes reales

- Configurar y comprobar los proveedores de verificación en el entorno del equipo, sin inventar credenciales ni prometer envíos desde un proveedor deshabilitado.
- Seguimiento del ambulante en segundo plano: requiere entrega específica con permisos, servicio/tarea y build nativo. El modo actual explica que funciona mientras se usa Ñapa.
- Decidir si se necesita SSE/WebSocket estricto además del sondeo y push existentes.
- Cambios de política de pagos, stock, límites o contraseñas requieren reflejar el nuevo acuerdo en specs; el informe no sustituye esas reglas por sí solo.

Git: el commit `a561f42` sí quedó creado por el usuario y la carpeta estaba limpia antes de este bloque. No se ejecutaron git add/commit/push ni cambios de rama.
