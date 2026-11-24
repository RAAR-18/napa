# Continuidad entre chats — Ñapa

Actualizado: 24 de noviembre de 2026.

- Repositorio real: `C:\Users\ESTUDIANTE\Documents\GitHub\napa`. No trabajar en la copia de `Documents\ChatGPT\napa`.
- Rama: `fix/p0-01-outbox-web`. Último commit confirmado del usuario: `a561f42` (`fix: corregir incidencias de auditoria del frontend`). La carpeta estaba limpia al empezar el bloque actual.
- Autorización: edición directa y comprobaciones por bloques. El usuario hace commits/push y un tester del grupo verifica cumplimiento funcional. No detener el avance esperando pruebas manuales ni repetir suites correctas sin motivo.
- El bloque anterior corrigió almacenamiento web, carrito/dirección, horarios, navegación, mensajes, calificaciones, selector de reserva y refresco de pantallas. Tipos/lint, pruebas afectadas y exportación web pasaron. El selector nativo de reserva requiere regenerar el build de desarrollo Android/iOS.
- Trabajo actual: P2-01, texto personalizado opcional para OTROS, 1–80 caracteres, sin cambiar el enum; preservado al editar/eliminar y expuesto en las consultas. Se actualizó spec, plan, modelo compartido, requerimiento, HU-53 y trazabilidad.
- Migración nueva `backend/src/main/resources/db/migration/V21__emprendimiento_categoria_personalizada.sql`. No se modificaron V1–V20 ni se ejecutó sobre la base local; las integraciones usan contenedores aislados.
- Piezas backend: `emprendimientos/domain/model/Emprendimiento.java`, command/DTOs/puertos, casos de uso de crear/listar/detalle, JPA y proyección JDBC. Se mantienen constructores previos para compatibilidad de llamadas existentes.
- Piezas frontend: `src/lib/emprendimientos.ts`, formulario de creación y consultas de negocio/listado/detalle. Campo visible solo en OTROS, opcional; categoría inmutable tras crear.
- P1-05 parcial: nuevo `frontend/src/lib/ubicacion-automatica.ts`, iniciado desde `_layout.tsx` y activable/desactivable desde `mi-emprendimiento.tsx`. Usa expo-location existente; cada 2 min en primer plano, mínimo 100 m. Preferencia por cuenta/negocio en almacenamiento existente; verificaciones de rol/propietario/generación/permiso y llamadas serializadas. No se guardan coordenadas viejas para reintento.
- El seguimiento en segundo plano NO está implementado ni se solicita su permiso. La interfaz explica el modo con Ñapa abierta. Este bloque no añade módulos nativos.
- Comprobación inicial backend de emprendimientos y arquitectura: correcta, V21 cargada en PostgreSQL aislado sin omisiones. Pruebas finales nuevas: 11 backend correctas, sin errores/fallos/omisiones; 12 frontend en la repetición final correctas, más las pantallas de consulta que ya habían pasado. La prueba que agotó 5 s con Java en paralelo pasó aislada sin aumentar el timeout. Typecheck, lint y exportación web finales correctos.
- Referencia vigente de incidencias: `docs/CORRECCIONES_AUDITORIA.md`. P1-01 de pagos y límites/contraseñas se contrastaron con specs y no se cambiaron silenciosamente. Mapas/rutas optimizadas siguen pospuestos.
- No se hizo git add, commit, push ni cambio de rama en este bloque.
