package co.edu.napa.calificaciones.application.usecase;

import co.edu.napa.calificaciones.application.ports.in.CalificarPort;
import co.edu.napa.calificaciones.application.ports.out.CalificacionRepositoryPort;
import co.edu.napa.calificaciones.application.ports.out.InteraccionesPort;
import co.edu.napa.calificaciones.application.ports.out.InteraccionesPort.ParticipantesPedido;
import co.edu.napa.calificaciones.domain.model.Calificacion;
import co.edu.napa.calificaciones.domain.model.RolUsuario;
import co.edu.napa.calificaciones.domain.model.TipoEntidad;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * RF-01. Orden de los errores: datos inválidos (400), pedido inexistente o ajeno (404), pedido no entregado (409),
 * destino no permitido (404) y repetida (409). Así un extraño no descubre el estado de un pedido que no es suyo.
 */
@Component
public class CalificarUseCase implements CalificarPort {
    private static final String NO_ENCONTRADO = "No encontramos lo que quieres calificar.";
    private static final String ENTREGADO = "ENTREGADO";

    private final CalificacionRepositoryPort calificaciones;
    private final InteraccionesPort interacciones;
    private final Clock clock;

    public CalificarUseCase(CalificacionRepositoryPort calificaciones, InteraccionesPort interacciones, Clock clock) {
        this.calificaciones = Objects.requireNonNull(calificaciones, "calificaciones");
        this.interacciones = Objects.requireNonNull(interacciones, "interacciones");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public Calificacion calificar(UUID autorId, RolUsuario rol, NuevaCalificacion datos) {
        Objects.requireNonNull(autorId, "autorId");
        Objects.requireNonNull(rol, "rol");
        Objects.requireNonNull(datos, "datos");
        if (rol == RolUsuario.ADMINISTRADOR) {
            throw new DomainException("FORBIDDEN", "No tienes permiso para realizar esta acción.");
        }
        Instant ahora = clock.instant();
        // Valida puntaje y comentario antes de mirar el pedido (400 primero).
        Calificacion calificacion = Calificacion.nueva(autorId, datos.pedidoId(), datos.tipoEntidad(),
                datos.entidadId(), datos.puntaje(), datos.comentario(), ahora);

        ParticipantesPedido pedido = interacciones.participantes(datos.pedidoId())
                .filter(p -> participa(autorId, rol, p))
                .orElseThrow(() -> new DomainException("NOT_FOUND", NO_ENCONTRADO));
        if (!ENTREGADO.equals(pedido.estado())) {
            throw new DomainException("INVALID_STATE", "Solo puedes calificar pedidos entregados.",
                    Map.of("estadoActual", pedido.estado()));
        }
        if (datos.entidadId().equals(autorId) || !destinoPermitido(rol, pedido, datos.tipoEntidad(), datos.entidadId())) {
            throw new DomainException("NOT_FOUND", NO_ENCONTRADO);
        }
        if (calificaciones.existe(autorId, datos.pedidoId(), datos.tipoEntidad(), datos.entidadId())) {
            throw yaCalificado();
        }
        calificaciones.insertar(calificacion);
        return calificacion;
    }

    @Override
    @Transactional(readOnly = true)
    public Opciones opciones(UUID usuarioId, RolUsuario rol, UUID pedidoId, UUID domicilioId) {
        Objects.requireNonNull(usuarioId, "usuarioId");
        if ((pedidoId == null) == (domicilioId == null)) {
            throw new DomainException("VALIDATION_ERROR", "Indica el pedido o el domicilio.",
                    Map.of("fields", Map.of("pedidoId", "Indica pedidoId o domicilioId")));
        }
        UUID idPedido = pedidoId != null ? pedidoId : interacciones.pedidoDeDomicilio(domicilioId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", NO_ENCONTRADO));
        ParticipantesPedido pedido = interacciones.participantes(idPedido)
                .filter(p -> participa(usuarioId, rol, p))
                .orElseThrow(() -> new DomainException("NOT_FOUND", NO_ENCONTRADO));
        if (!ENTREGADO.equals(pedido.estado())) {
            return new Opciones(idPedido, pedido.estado(), java.util.List.of());
        }
        Map<String, UUID> hechas = new java.util.HashMap<>();
        calificaciones.delAutorEnPedido(usuarioId, idPedido)
                .forEach(c -> hechas.put(c.tipoEntidad() + ":" + c.entidadId(), c.id()));
        java.util.List<Opcion> opciones = new java.util.ArrayList<>();
        java.util.function.BiConsumer<TipoEntidad, UUID> agregar = (tipo, id) -> {
            if (id == null || id.equals(usuarioId) || !destinoPermitido(rol, pedido, tipo, id)) {
                return;
            }
            String rolDestino = tipo != TipoEntidad.USUARIO ? null
                    : id.equals(pedido.clienteId()) ? "CLIENTE" : "DOMICILIARIO";
            opciones.add(new Opcion(tipo, id, interacciones.nombre(tipo, id).orElse(null), rolDestino,
                    hechas.get(tipo + ":" + id)));
        };
        agregar.accept(TipoEntidad.EMPRENDIMIENTO, pedido.emprendimientoId());
        pedido.productoIds().stream().sorted().forEach(id -> agregar.accept(TipoEntidad.PRODUCTO, id));
        agregar.accept(TipoEntidad.USUARIO, pedido.clienteId());
        agregar.accept(TipoEntidad.USUARIO, pedido.domiciliarioId());
        return new Opciones(idPedido, pedido.estado(), opciones);
    }

    static DomainException yaCalificado() {
        return new DomainException("CONFLICT", "Ya calificaste esto.");
    }

    /** El domiciliario participa solo si entregó el domicilio del pedido. */
    private static boolean participa(UUID autorId, RolUsuario rol, ParticipantesPedido pedido) {
        return switch (rol) {
            case CLIENTE -> autorId.equals(pedido.clienteId());
            case VENDEDOR_AMBULANTE, VENDEDOR_PUNTO_FIJO -> autorId.equals(pedido.vendedorId());
            case DOMICILIARIO -> autorId.equals(pedido.domiciliarioId());
            case ADMINISTRADOR -> false;
        };
    }

    /** Tabla de autorización del plan de RF-01. */
    private static boolean destinoPermitido(RolUsuario rol, ParticipantesPedido pedido, TipoEntidad tipo, UUID entidadId) {
        boolean esEmprendimiento = tipo == TipoEntidad.EMPRENDIMIENTO && entidadId.equals(pedido.emprendimientoId());
        boolean esProducto = tipo == TipoEntidad.PRODUCTO && pedido.productoIds().contains(entidadId);
        boolean esCliente = tipo == TipoEntidad.USUARIO && entidadId.equals(pedido.clienteId());
        boolean esDomiciliario = tipo == TipoEntidad.USUARIO && entidadId.equals(pedido.domiciliarioId());
        return switch (rol) {
            case CLIENTE -> esEmprendimiento || esProducto || esDomiciliario;
            case VENDEDOR_AMBULANTE -> esCliente;
            case VENDEDOR_PUNTO_FIJO -> esCliente || esDomiciliario;
            case DOMICILIARIO -> esEmprendimiento || esCliente;
            case ADMINISTRADOR -> false;
        };
    }
}
