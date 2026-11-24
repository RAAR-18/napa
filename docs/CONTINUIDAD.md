# Continuidad entre chats

Actualizado: 24 de noviembre de 2026.

- Carpeta real: `C:\Users\ESTUDIANTE\Documents\GitHub\napa`; no trabajar en la copia de `Documents\ChatGPT\napa`.
- Rama: `fix/p0-01-outbox-web`; base `35cc46d`.
- El usuario autorizó correcciones directas y pruebas; commits/push siguen siendo manuales.
- Trabajo actual: correcciones del informe técnico. Mapas, navegación y optimización de varias entregas están pospuestos.
- P0-01: adaptador web IndexedDB ya incorporado por el usuario; se añadió manejo de errores de apertura y cobertura de persistencia/concurrencia. Se mantiene SQLite nativo.
- P0-03: nuevo `frontend/src/components/TimeField.tsx` y uso en creación/edición de emprendimiento para restringir y formatear horas.
- Validación: 293 pruebas frontend/43 suites, sin fallos; typecheck y lint correctos. Prueba manual web con publicación, recarga offline y reconexión pendiente. No se ejecutaron pruebas backend, pues no hubo cambios allí.
- Dependencia nueva de pruebas: `fake-indexeddb`. La instalación npm completó; Expo finalizó con un error de recarga de CLI durante la instalación. Las verificaciones posteriores de Jest, tipos y lint funcionaron.
- No se migraron pendientes antiguos de SQLite web ni se cambiaron otras cachés SQLite. No borrar datos del navegador.
- Próximo objetivo: P0-06, persistencia automática de carrito. Archivos clave: `frontend/src/components/PedidoCatalogo.tsx`, `frontend/src/lib/pedido-offline.ts` y pruebas del catálogo/productos.
- No aplicar ciegamente los cambios de política de contraseñas, límites o inventario del informe: contrastar specs/planes.
- Leer `docs/CORRECCIONES_AUDITORIA.md` para detalles y limitaciones. Los planes de fundaciones, RF-28 y RF-29 registran esta entrega.
- No se creó commit ni push. La selección previa de archivos preparados se conservó.

Comprobación final: exportación web de Expo correcta; adaptador IndexedDB presente en el bundle generado. La CLI funcionó tras terminar la instalación. Continúa pendiente únicamente el recorrido manual de esta entrega en navegador y la decisión de commit del usuario.

## Segundo bloque y nueva forma de avanzar

El usuario pidió corregir por bloques, con pruebas pertinentes y compilación, y delegó la validación funcional en el tester del grupo. No esperar pruebas manuales para seguir. Docker disponible si se necesitan integraciones.

Aplicados: persistencia automática de carrito y última dirección (P0-06/P1-03), acceso a edición de negocio (P1-06), navegación móvil única (P2-03), aviso de éxito de pagos (P2-04), botón de calificar según opciones pendientes (P2-05), textos de registro y acceso unificado a cuenta (P3-01/P3-02). Validación de cantidades y feedback de contraseñas mejorados sin alterar decisiones funcionales (P0-02/P0-04 parciales).

Archivos nuevos importantes: `frontend/src/lib/pedido-storage.ts`, `pedido-storage.web.ts`, `frontend/src/hooks/useCalificacionPendiente.ts`. `pedido-offline.ts` conserva la API pública y delega almacenamiento por plataforma. `PedidoCatalogo.tsx` gestiona recuperación, guardado serializado y limpieza de borrador. No se modificó el backend en este bloque.

Siguiente trabajo: proveedor y recuperación de verificación P0-05, pagos de reservas P1-01 y restantes pendientes listados en `CORRECCIONES_AUDITORIA.md`. Mantener separados cambios funcionales propuestos por el informe de errores confirmados contra las specs. No se hicieron commits/push ni cambios en la selección de archivos del usuario.

Validación final del segundo bloque: typecheck/lint correctos; exportación web correcta; 41 suites correctas en la comprobación general y las pruebas afectadas corregidas y verificadas en una repetición dirigida de 5 suites/55 pruebas. No repetir toda la suite sin nuevos cambios o fallos. El recorrido funcional queda a cargo del tester por instrucción del usuario.

## Último bloque (reemplaza los próximos pasos anteriores)

- Revisión solicitada del commit: todavía no existe un commit de estas correcciones; HEAD `35cc46d`. El usuario había preparado el bloque anterior. Git tiene autor configurado. Conservar su índice; no hacer commit/push.
- Añadidos selector visual de reserva (P1-07), actualización visible cada 30 s de notificaciones/pedidos (P1-02, sondeo, no SSE) y mejoras de verificación por correo y bloqueo de operaciones concurrentes (P0-05 parcial).
- Archivos nuevos: `frontend/src/components/ReservationDateTimeField.tsx`, su variante `.web.tsx`, `frontend/src/lib/reservation-date.ts` y `frontend/src/hooks/useAutoRefresh.ts` (con pruebas). Nuevo módulo compatible Expo: `@react-native-community/datetimepicker` 9.2.1; plugin en `frontend/app.config.js`. El build de desarrollo Android/iOS debe regenerarse para incorporar el módulo.
- Comprobaciones: tipos/lint y exportación web correctos; pruebas afectadas de compra/pedidos correctas, más repetición dirigida final de 22 pruebas en 4 suites, sin fallos. No repetir toda la suite sin motivo. No hubo cambios backend ni mensajes externos.
- Hallazgos del informe que contradicen decisiones vigentes: cobro de recogida/directa al entregar (RF-42 FR-002), límites acordados RF-45, y dirección opcional del ambulante RF-28 FR-005. No convertirlos en cambios de alcance automáticamente.
- Pendientes: verificación real de configuración de proveedores por el equipo, ubicación periódica P1-05, y decidir el alcance de categoría personalizada P2-01 o SSE estricto. Mapas y optimización siguen pospuestos. Ver CORRECCIONES_AUDITORIA para estado completo.
