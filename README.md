# Ñapa

Plataforma para conectar a vendedores informales y pequeños comerciantes de las grandes ciudades con clientes cercanos,
permitiendo publicar productos, gestionar pedidos anticipados y coordinar domicilios sin abandonar el punto de venta.

## Problema

Vendedores informales (carretillas, puestos ambulantes, pequeños negocios familiares)
dependen del tránsito peatonal para vender y no tienen forma de dar visibilidad a su oferta,
recibir pedidos anticipados ni coordinar entregas sin dejar su punto de venta.
Esto es especialmente crítico para productos perecederos (frutas, pescado, alimentos preparados),
donde lo no vendido en el día se pierde o pierde valor.

## Estructura del repositorio

```
├── capstone/
│   └── informe-capstone.md      # Entregable oficial (plantilla del curso, link al documento word)
├── investigacion/               # Entrevistas, mapa de empatía, hallazgos
│   └── HistoriasDeUsuario/      # Historias de usuario con su actor y caso de uso
├── requerimientos/              # Requerimientos funcionales y no funcionales, matriz de trazabilidad
├── casos de uso/                # Diagramas de casos de uso (PlantUML), uno por módulo
├── specs/                       # Specs y planes (ver specs/README.md)
│   ├── templates/               # spec-template.md y plan-template.md
│   ├── general/                 # Roles, convenciones de API, modelo de datos (ER) y catálogo de eventos
│   ├── foundations/plan.md      # Plan de la infraestructura transversal
│   └── RF-NN-nombre/              # RF-NN-nombre.md (QUÉ) y plan.md (CÓMO) por requerimiento
├── backend/                     # Spring Boot, arquitectura hexagonal por módulo
└── frontend/                    # React Native + Expo
```

## Actores principales

- **Cliente**: descubre emprendimientos cercanos y hace pedidos en tres modalidades: entrega directa, recogida en punto fijo o domicilio. Cualquier pedido puede marcarse como reserva.
- **Vendedor**: administra su emprendimiento y sus productos y atiende pedidos. Tiene dos perfiles con flujos distintos:
  - **Vendedor ambulante**: atiende *entregas directas* (va hasta donde está el cliente), inmediatas o marcadas como reserva.
  - **Vendedor de punto fijo**: atiende *recogidas* en su punto (inmediatas o reservas) y ofrece *domicilios* mediante domiciliarios.
- **Domiciliario**: toma domicilios disponibles (con la ganancia visible), los acepta con la tarifa publicada o los rechaza de forma definitiva; no negocia el precio. Recoge el pedido en el punto fijo y lo lleva al cliente.
- **Administrador**: supervisa usuarios, domicilios, pagos, comentarios y reportes, y define la tarifa mínima de domicilio.

## Modalidades de entrega

| Modalidad | Vendedor ambulante | Vendedor de punto fijo |
|---|:---:|:---:|
| Entrega directa | ✅ | — |
| Recogida en punto fijo | — | ✅ |
| Domicilio (punto A → punto B, no editable) | — | ✅ |

**Reserva** no es una modalidad: es un atributo de cualquier pedido, con fecha y hora acordadas con el vendedor, que este acepta o rechaza con su propio criterio.

## Pagos

La plataforma **no procesa dinero**: solo registra la confirmación de cada cobro. Medios admitidos:

- **Efectivo** o **transferencia a la cuenta bancaria del vendedor** en entrega directa y recogida, según lo que el vendedor acepte.
- **Domicilio: solo transferencia digital.** El cliente paga un total (productos + tarifa) apenas el vendedor acepta el pedido; el vendedor confirma el pago antes de publicar el domicilio. Tras la entrega, el vendedor transfiere la tarifa al domiciliario y ambos lo confirman.

No se guardan datos de tarjetas ni billeteras electrónicas de terceros.

## Stack previsto

Backend: Java 21, Spring Boot 4.1.x, Spring Security, JPA + Hibernate Spatial, PostgreSQL 16 + PostGIS, Redis 7, Firebase Admin (FCM), Twilio Verify (verificación del celular por WhatsApp o SMS). Frontend: React Native + Expo (development build), expo-sqlite. Detalle y decisiones en [`specs/foundations/plan.md`](specs/foundations/plan.md).

## Desarrollo local

Requisitos: JDK 21, Node.js 24 o compatible con Expo SDK 58 y Docker Compose.

```powershell
docker compose up -d
cd backend
.\gradlew.bat test
```

Para iniciar la API localmente, conserva la base de datos de Compose y ejecuta:

```powershell
$env:DATABASE_PASSWORD = "napa_local_dev_only"
$env:NAPA_JWT_SECRET = "replace-with-at-least-32-random-local-bytes"
.\gradlew.bat bootRun
```

El secreto JWT es obligatorio y debe tener al menos 32 bytes. Para crear el administrador inicial, configura una sola vez `NAPA_ADMIN_BOOTSTRAP_ENABLED=true`, `NAPA_ADMIN_NAME`, `NAPA_ADMIN_PHONE` (formato E.164) y `NAPA_ADMIN_PASSWORD` (8–128 caracteres). El bootstrap se marca como completado en la base de datos; no vuelvas a habilitarlo para reemplazar la cuenta.

La integración de entrega OTP con Twilio está diferida hasta aprobar presupuesto. Mientras no exista un proveedor de verificación configurado, las rutas de verificación responden `503`; no se escribe ni registra ningún código OTP.

Para habilitar el cálculo de rutas, configura una clave de OpenRouteService con Routes API habilitada:

```powershell
$env:ORS_API_KEY = "tu-clave-local"
```

En otra terminal, prepara y valida la app móvil:

```powershell
cd frontend
npm ci
npm run typecheck
npm start
```

El entorno de Compose usa credenciales exclusivamente locales. No las reutilices en producción.

## Estado del proyecto

La implementación cubrió las fundaciones F0–F1 y avanza en F2 (identidad). El estado de cada fase, las decisiones aprobadas y los bloqueos de proveedor están en [el plan de fundaciones](specs/foundations/plan.md).