package co.edu.napa.domicilios.application.usecase;

import co.edu.napa.cuentas.application.ports.in.ConsultarNombresUsuarioPort;
import co.edu.napa.domicilios.application.ports.in.ListarDomiciliosVendedorPort;
import co.edu.napa.domicilios.application.ports.out.DomicilioRepositoryPort;
import co.edu.napa.domicilios.domain.model.Domicilio;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class ListarDomiciliosVendedorUseCase implements ListarDomiciliosVendedorPort {
    private static final int MAX_SIZE = 100;

    private final DomicilioRepositoryPort domicilios;
    private final ConsultarNombresUsuarioPort usuarios;

    public ListarDomiciliosVendedorUseCase(DomicilioRepositoryPort domicilios, ConsultarNombresUsuarioPort usuarios) {
        this.domicilios = Objects.requireNonNull(domicilios, "domicilios");
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina listar(UUID vendedorId, Filtro filtro, int page, int size) {
        if (vendedorId == null) {
            throw new DomainException("VALIDATION_ERROR", "Indica el vendedor.");
        }
        if (page < 0) {
            throw validation("page", "La página no puede ser negativa.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw validation("size", "El tamaño de página debe estar entre 1 y 100.");
        }
        Filtro efectivo = filtro == null ? Filtro.SIN_FILTROS : filtro;
        if (efectivo.desde() != null && efectivo.hasta() != null && !efectivo.desde().isBefore(efectivo.hasta())) {
            throw validation("desde", "La fecha inicial no puede ser posterior a la fecha final.");
        }
        List<Domicilio> filas = domicilios.listarVendedor(vendedorId, efectivo, (long) page * size, size);
        long total = domicilios.contarVendedor(vendedorId, efectivo);
        Set<UUID> domiciliarios = filas.stream().map(Domicilio::domiciliarioId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> nombres = domiciliarios.isEmpty() ? Map.of() : usuarios.consultar(domiciliarios);
        List<Item> items = filas.stream().map(domicilio -> new Item(
                domicilio.id(),
                domicilio.pedidoId(),
                domicilio.estado(),
                domicilio.tarifa(),
                domicilio.domiciliarioId() == null ? null
                        : new Domiciliario(nombres.get(domicilio.domiciliarioId())),
                domicilio.motivoCancelacion() == null ? null : domicilio.motivoCancelacion().name(),
                domicilio.createdAt(),
                domicilio.updatedAt())).toList();
        return new Pagina(items, page, size, total);
    }

    private static DomainException validation(String field, String message) {
        return new DomainException("VALIDATION_ERROR", message, Map.of("fields", Map.of(field, message)));
    }
}
