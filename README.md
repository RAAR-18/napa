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
└── specs/                       # Especificación detallada de cada requerimiento funcional (RF-NN)
```

## Actores principales

- **Cliente**: descubre emprendimientos cercanos y hace pedidos en tres modalidades: entrega directa, reserva o domicilio.
- **Vendedor**: administra su emprendimiento (abierto o cerrado) y sus productos y atiende pedidos. Tiene dos perfiles con flujos distintos:
  - **Vendedor ambulante**: atiende *entregas directas* (va hasta donde está el cliente) y *reservas* para el día siguiente con entrega al cliente.
  - **Vendedor de punto fijo**: atiende *reservas* que el cliente retira en el punto fijo y ofrece *domicilios* mediante domiciliarios.
- **Domiciliario**: toma domicilios disponibles (con la ganancia visible), puede ofertar otro precio, recoge el pedido en el punto fijo y lo lleva al cliente.
- **Administrador**: supervisa usuarios y domicilios de la plataforma.

## Modalidades de entrega

| Modalidad | Vendedor ambulante | Vendedor de punto fijo |
|---|:---:|:---:|
| Entrega directa | ✅ | — |
| Reserva (para el día siguiente, según la disponibilidad prevista) | ✅ entrega al cliente | ✅ el cliente retira en el punto fijo |
| Domicilio (punto A → punto B, no editable) | — | ✅ |

## Métodos de pago

| Vendedor | Modalidad | Efectivo | Transferencia |
|---|---|:---:|:---:|
| Ambulante | Entrega directa | ✅ | ✅ |
| Ambulante | Reserva (entrega al cliente) | ✅ | ✅ |
| Punto fijo | Reserva (el cliente retira) | ✅ | ✅ |
| Punto fijo | Domicilio | — | ✅ |

La plataforma no procesa el dinero: registra el método elegido y la confirmación del pago.

## Estado del proyecto

En construcción. Fase actual: documentación de diseño (casos de uso, historias de usuario, requerimientos y especificaciones).
