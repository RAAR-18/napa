# Continuidad entre chats — Ñapa

Actualizado: 24 de noviembre de 2026.

## Repositorio y colaboración

- Carpeta real: `C:\Users\ESTUDIANTE\Documents\GitHub\napa`. No modificar la copia Documents/ChatGPT.
- Rama `fix/p0-01-outbox-web`; último commit confirmado `180109b`, cachés web de listado/rutas. Carpeta limpia al iniciar este bloque.
- Edición directa y comprobaciones autorizadas. Trabajar por bloques, repetir solo lo afectado por cambios/fallos. El tester valida recorridos funcionales.
- Git manual: entregar siempre el bloque completo de Git Bash al recomendar commit, con archivos explícitos. No ejecutar add/commit/push ni gestionar ramas por iniciativa propia. Preferencia guardada en AGENTS.md.

## Bloques integrados

- Outbox/carrito web, dirección guardada, horarios, navegación, calificaciones, selector de reserva, refresco visible de pedidos/notificaciones.
- Categoría Otros personalizada (V21) y ubicación ambulante voluntaria en primer plano: commit b8a8cc4.
- Verificación SMS/correo con concurrencia y cupos Redis: commit 817acbe; 18 pruebas unitarias/caso de uso y 7 Redis aislado correctas. Sin envíos reales ni secretos modificados.
- Listado/rutas con almacenamiento separado por plataforma: commit 180109b; 21 pruebas dirigidas, tipos/lint/exportación web correctos.

## Bloque actual sin commit: seis cachés web por cuenta (P0-01)

- Nuevas variantes `.web.ts`: perfil-cache, emprendimiento-propio-cache, inventario-cache, catalogo-cache, notificaciones-cache y pedidos-cliente-cache.
- Motor común `frontend/src/lib/cache-cuenta.web.ts`: transacciones IndexedDB completas, índice cuenta/espacio, vigencia siete días. Límites de 50 páginas por cuenta; consultas de pedidos por tipo. Perfil/negocio una copia. Sesión revalidada al escribir. El error revierte el cambio completo.
- Se conservan minimización de datos, filtros, borrado por propietario, renovación de inventario al cambiar de negocio y eliminación atómica de pedido cancelado de detalle/listados. Implementaciones SQLite nativas intactas.
- Pruebas nuevas `frontend/src/lib/caches-cuenta.web.test.ts`; plan foundations y documentación actualizados. Sin dependencias, API, migraciones o servicios nuevos.
- Validación: 12 pruebas web nuevas y 3 de perfil nativo correctas. Lint completo y dirigido final correctos. Exportación web correcta y sin recursos WASM/worker de SQLite en la salida generada. Tipos finales correctos.
- No se migran cachés web antiguas: se regeneran consultando en línea. El outbox SQLite web anterior no se migra; sincronizar pendientes antes de desplegar. No borrar datos del navegador como solución.

## Pendientes y próximo paso

- Terminar comprobaciones del bloque y entregar los comandos de commit al usuario. La revisión funcional en navegador/móvil la realiza el tester; no afirmar que se hizo.
- Detalle de todas las incidencias: `docs/CORRECCIONES_AUDITORIA.md`. Las variantes web completan la separación de almacenamiento de las cachés identificadas; P0-01 queda implementado, pendiente de aceptación funcional.
- Proveedores SMSReactor/Brevo requieren configuración operativa del equipo. No solicitar secretos completos ni enviar mensajes reales sin necesidad autorizada.
- Ubicación automática ambulante solo en primer plano; segundo plano requiere alcance/build/permisos específicos. Notificaciones usan sondeo visible/push, no SSE estricto.
- Pagos anticipados, límite de 20 y composición de contraseña dependen de specs; el informe no redefine sus acuerdos.
- Mapas visuales y rutas optimizadas siguen pospuestos hasta cerrar auditoría y confirmar alcance.
- Selector nativo de reserva de un bloque anterior requiere regenerar el build Android/iOS; este bloque no añade dependencias nativas.
