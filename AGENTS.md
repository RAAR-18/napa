# Instrucciones de colaboración — Ñapa

Estas instrucciones aplican a todo el repositorio. En `frontend/` se leen también sus instrucciones locales; ante conflicto de procedimiento prevalece el modo manual solicitado aquí por el usuario.

## Inicio de cada chat

1. Leer este archivo, `docs/CONTEXTO_PROYECTO.md` y `docs/CONTINUIDAD.md`.
2. Identificar el objetivo y RF del trabajo actual. Consultar solamente la spec, el plan y las reglas compartidas pertinentes.
3. No volver a recorrer todo el repositorio. El mapa permite localizar las piezas; si falta código vigente, pedir al usuario el archivo o método concreto.
4. No asumir que otro chat tiene acceso a conversaciones anteriores. La continuidad se conserva en archivos, siempre que el usuario haya pegado y guardado la última versión.

## Forma de trabajar acordada

- Responder en español, con explicaciones claras y concretas.
- El usuario aplica los cambios manualmente: entregar código para copiar y pegar. No editar código, instalar dependencias, ejecutar migraciones, iniciar servicios ni modificar datos sin una solicitud explícita que autorice esa acción.
- La creación inicial de estos documentos de contexto fue solicitada expresamente. Para actualizaciones posteriores, entregar también el texto para que el usuario lo pegue, salvo autorización expresa de edición.
- Antes de pedir código, usar el mapa. Pedir la ruta exacta y el nombre del método, clase o bloque requerido, explicando para qué se necesita. Agrupar los archivos imprescindibles en una sola solicitud cuando sea posible.
- Preferir fragmentos acotados. Pedir el archivo completo cuando los imports, tipos o contexto sean necesarios; no inventar firmas ni proponer reemplazos basados en versiones supuestas.
- No pedir credenciales, archivos `.env` completos, tokens, claves bancarias ni datos personales reales. Solicitar ejemplos anonimizados.
- Una inspección puntual de archivos disponibles es válida si resulta necesaria; evitar búsquedas masivas y repetidas. Para una revisión global solicitada expresamente, sí revisar la estructura y la evidencia pertinente.

## Formato de cada propuesta de cambio

1. **Ruta:** ruta relativa al repositorio, y enlace absoluto si el archivo está disponible localmente.
2. **Ubicación:** clase, método, componente o texto de referencia exacto. Indicar si se crea un archivo, se reemplaza el archivo entero, se reemplaza un método o se inserta un bloque.
3. **Código:** bloque completo de la unidad que se reemplaza, con imports adicionales y sin omitir partes necesarias mediante puntos suspensivos.
4. **Por qué:** problema concreto que resuelve y motivo de la decisión.
5. **Cómo funciona:** relación con las otras piezas y efectos sobre API, datos, permisos o interfaz cuando corresponda.
6. **Validación manual:** pasos o comandos que el usuario debe ejecutar y resultado esperado. Distinguir siempre lo propuesto, lo pegado por el usuario y lo verificado.

No afirmar que algo está aplicado o que una prueba pasó sin evidencia. Si una instrucción local exige pruebas, entregar los comandos al usuario en este modo manual y mantener la validación como pendiente hasta recibir resultados.

## Commits y cierre

- Nunca ejecutar `git add`, `git commit`, `git push`, merges ni crear PR por iniciativa propia. El usuario gestiona Git manualmente.
- Al terminar una unidad coherente y validada, avisar: **“Es buen momento para hacer un commit”**, listar los archivos que deberían entrar y sugerir un mensaje. Si faltan comprobaciones, decirlo y no presentar el hito como validado.
- Al cerrar una sesión extensa, entregar una actualización para `docs/CONTINUIDAD.md`: objetivo, decisiones y motivos, archivos afectados, validación observada, pendientes y siguiente paso.
- Cambios de arquitectura o reglas deben reflejarse en las specs y planes correspondientes; el contexto es un índice, no reemplaza esas fuentes.

## Reglas técnicas que se deben conservar

- Desarrollo guiado por especificaciones: QUÉ en la spec, CÓMO en el plan, después código. Cambios de alcance actualizan requerimientos, historias y trazabilidad.
- Backend hexagonal por módulo: dominio sin Spring/JPA y comunicación entre módulos mediante puertos. No saltar capas para resolver una tarea rápidamente.
- No reescribir migraciones ya aplicadas. Proponer nuevas migraciones Flyway y explicar compatibilidad y consecuencias.
- Mantener autorización por propietario/rol, privacidad de ubicación, idempotencia, transacciones, concurrencia e integridad del outbox.
- No confundir reserva con modalidad de entrega ni registro de pagos con procesamiento de dinero.
- No cambiar versiones o proveedores sin justificarlo y consultar la configuración vigente. Para APIs Expo/React Native, respetar la comprobación de documentación versionada indicada en `frontend/AGENTS.md`.

## Modo autorizado para la etapa actual (2026-11-24)

El usuario autorizó trabajar directamente corrigiendo errores en `C:\Users\ESTUDIANTE\Documents\GitHub\napa`: se pueden editar los archivos y ejecutar las comprobaciones necesarias. Durante esta etapa no es necesario entregar cada cambio para que lo pegue manualmente. Se mantienen las explicaciones con rutas, motivo y validación. Los commits, push y gestión de ramas siguen a cargo del usuario. No trasladar los cambios a la copia anterior de `Documents\ChatGPT\napa`.

## Ritmo de corrección acordado (2026-11-24)

Priorizar corregir incidencias por bloques y mantener el proyecto compilando y las pruebas pertinentes pasando. Ejecutar una comprobación conjunta y repetir solo lo afectado por fallos o cambios posteriores. Evitar ampliar la revisión funcional o repetir comprobaciones correctas sin motivo. El tester del grupo valida los recorridos y el cumplimiento funcional. Docker está disponible si se necesitan integraciones backend. No detener el avance esperando pruebas manuales del tester ni prometer que ya se realizaron.

## Formato de commits solicitado

Siempre que se recomiende un commit, entregar el bloque completo para pegar en Git Bash: ruta real del repositorio, git add con archivos explícitos, revisión de lo preparado, git commit con mensaje y git status. No ejecutar esas operaciones por el usuario ni incluir push automáticamente.
