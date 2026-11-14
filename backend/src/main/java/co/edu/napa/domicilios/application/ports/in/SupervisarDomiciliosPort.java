package co.edu.napa.domicilios.application.ports.in;

import co.edu.napa.domicilios.domain.model.EstadoDomicilio;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * RF-12: el administrador lista y consulta cualquier domicilio. Nunca incluye el código de confirmación ni datos
 * de contacto: solo ids y nombres.
 */
public interface SupervisarDomiciliosPort {
    Pagina listar(Filtro filtro, int page, int size);

    Detalle consultar(UUID domicilioId);

    /** {@code desde} inclusivo y {@code hasta} exclusivo sobre la fecha de publicación. */
    record Filtro(
            EstadoDomicilio estado,
            Instant desde,
            Instant hasta,
            UUID vendedorId,
            UUID domiciliarioId,
            boolean porActualizacion,
            boolean ascendente
    ) {
        public static final Filtro SIN_FILTROS = new Filtro(null, null, null, null, null, false, false);
    }

    record Pagina(List<Fila> content, int page, int size, long totalElements) {
        public Pagina {
            content = List.copyOf(content);
        }
    }

    /** {@code minutosSinTomar} solo en los {@code DISPONIBLE}; {@code domiciliario} nulo hasta que alguien lo acepta. */
    record Fila(
            UUID id,
            UUID pedidoId,
            EstadoDomicilio estado,
            long tarifa,
            Long minutosSinTomar,
            Punto origen,
            Punto destino,
            Persona vendedor,
            Persona cliente,
            Persona domiciliario,
            String motivoCancelacion,
            Instant createdAt,
            Instant updatedAt
    ) {}

    record Detalle(
            UUID id,
            UUID pedidoId,
            EstadoDomicilio estado,
            long tarifa,
            Long minutosSinTomar,
            Punto origen,
            Punto destino,
            Persona vendedor,
            Persona cliente,
            Persona domiciliario,
            Instant createdAt,
            Instant updatedAt,
            Pedido pedido,
            String motivoCancelacion,
            String detalleCancelacion,
            PagoTarifa pagoTarifa,
            List<Evento> trazabilidad
    ) {
        public Detalle {
            trazabilidad = List.copyOf(trazabilidad);
        }
    }

    record Punto(double latitud, double longitud, String direccion, String barrio) {}

    record Persona(UUID id, String nombre) {}

    record Pedido(UUID id, String modalidad, String estado, long total, int cantidadItems) {}

    record PagoTarifa(String estado, long monto) {}

    /** {@code autor} nulo cuando el cambio lo hizo el sistema. */
    record Evento(EstadoDomicilio estado, Instant ocurridoAt, Persona autor) {}
}
