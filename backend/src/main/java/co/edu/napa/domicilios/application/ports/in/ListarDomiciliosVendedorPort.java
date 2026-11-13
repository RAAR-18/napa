package co.edu.napa.domicilios.application.ports.in;

import co.edu.napa.domicilios.domain.model.EstadoDomicilio;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** RF-15: el vendedor de punto fijo lista y filtra sus domicilios. */
public interface ListarDomiciliosVendedorPort {
    Pagina listar(UUID vendedorId, Filtro filtro, int page, int size);

    /** {@code desde} inclusivo y {@code hasta} exclusivo sobre la fecha de publicación. */
    record Filtro(EstadoDomicilio estado, Instant desde, Instant hasta, boolean ascendente) {
        public static final Filtro SIN_FILTROS = new Filtro(null, null, null, false);
    }

    record Pagina(List<Item> content, int page, int size, long totalElements) {
        public Pagina {
            content = List.copyOf(content);
        }
    }

    record Item(
            UUID id,
            UUID pedidoId,
            EstadoDomicilio estado,
            long tarifa,
            Domiciliario domiciliario,
            String motivoCancelacion,
            Instant createdAt,
            Instant updatedAt
    ) {}

    record Domiciliario(String nombre) {}
}
