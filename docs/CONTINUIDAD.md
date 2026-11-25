# Continuidad entre chats — Ñapa

Actualizado: 24 de noviembre de 2026.

- Carpeta real: `C:\Users\ESTUDIANTE\Documents\GitHub\napa`. No usar la copia de Documents/ChatGPT.
- Rama: `fix/p0-01-outbox-web`. Último commit confirmado: `817acbe` (concurrencia y cupos de verificación). La carpeta estaba limpia al iniciar el bloque de cachés.
- Modo autorizado: editar y verificar directamente por bloques; el usuario realiza commits/push y el tester del grupo comprueba recorridos funcionales. No repetir suites correctas sin motivo ni esperar pruebas manuales para avanzar en correcciones autorizadas.
- Bloques ya integrados: almacenamiento web/carrito, direcciones, horarios, navegación, calificaciones, selector de reserva, refresco visible de notificaciones/pedidos, Otros personalizado (V21) y ubicación voluntaria del ambulante en primer plano.
- Bloque P0-05 ya integrado: `backend/src/main/java/co/edu/napa/cuentas/infrastructure/adapters/out/messaging/SmsCorreoVerificacionAdapter.java` corregido. Redis guarda código/intentos/generación/TTL atómicamente; confirma y registra intentos mediante CAS sobre el desafío leído, para no borrar ni penalizar un reenvío concurrente. Reserva/liberación de cupo atómicas y sin envíos si Redis no confirma. No se cambia el canal elegido ni la vigencia/intentos.
- Pruebas: `SmsCorreoVerificacionAdapterTest`, `SolicitarVerificacionUseCaseTest` y nueva `SmsCorreoVerificacionAdapterRedisIntegrationTest`. Pasaron 18 pruebas unitarias/de caso de uso y 7 de Redis aislado; cero fallos/errores/omisiones. Backend compilado. No hubo cambios frontend ni se repitieron sus pruebas.
- README corregido para SMSReactor/Brevo, registro sin envío inicial automático y variables de entorno del proveedor. No se inspeccionaron ni modificaron secretos, no se enviaron mensajes reales y no hubo migraciones nuevas en este bloque.
- Detalles y estado por incidencia: `docs/CORRECCIONES_AUDITORIA.md`; diseño de la corrección en el plan RF-70.
- Pendientes externos/de alcance: proveedores operativos en el entorno del equipo; ubicación en segundo plano (el modo actual solo funciona con Ñapa abierta); decidir SSE estricto. No retirar límites o cambiar pagos/contraseñas contra las specs por iniciativa propia.
- Mapas, selección visual de ubicaciones y rutas optimizadas continúan pospuestos por el usuario. No pasar a esa fase sin cerrar los pendientes de auditoría y confirmar el siguiente alcance.
- El build de desarrollo Android/iOS debe incorporar el selector nativo de reserva añadido en un bloque anterior. No se añadieron dependencias nativas en la corrección actual.
- No se ejecutaron git add/commit/push ni cambios de rama. El bloque actual sigue sin commit.


## Trabajo actual: P0-01, cachés de listado y rutas

Se extrajo `frontend/src/lib/cache-lectura-storage.ts` (SQLite nativo compatible) y `.web.ts` (IndexedDB). Listado de negocios y rutas guardadas lo usan conservando validaciones y borrado selectivo de direcciones. Pruebas nuevas en `cache-lectura-storage.web.test.ts` y `ruta-cache.web.test.ts`; plan foundations y RF-22 actualizados. Sin dependencias, migraciones, servicios o cambios de mapas.

Validación observada: 9 pruebas dirigidas de cachés y 12 de pantallas correctas; exportación web correcta. Tipos y lint finales correctos, sin errores ni advertencias. El bloque sigue sin commit y Git lo gestiona el usuario.

Siguiente paso: completar las otras seis cachés web aún SQLite (`perfil-cache`, `emprendimiento-propio-cache`, `inventario-cache`, `catalogo-cache`, `notificaciones-cache`, `pedidos-cliente-cache`). Preservar aislamiento por cuenta, vencimiento de siete días, límite de 50 registros/páginas y escrituras serializadas. Evitar anunciar P0-01 como resuelto globalmente. Mapas nuevos siguen pospuestos.
