package co.edu.napa.emprendimientos.application.usecase;

import co.edu.napa.emprendimientos.application.ports.in.ListarEmprendimientosPort;
import co.edu.napa.emprendimientos.application.ports.out.EmprendimientoListadoProjection;
import co.edu.napa.emprendimientos.application.ports.out.EmprendimientoListadoRepositoryPort;
import co.edu.napa.emprendimientos.application.ports.out.FiltroEmprendimientos;
import co.edu.napa.emprendimientos.domain.model.HorarioAtencion;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class ListarEmprendimientosUseCase implements ListarEmprendimientosPort {
    private static final int MAX_PAGE_SIZE = 100;
    private static final ZoneId COLOMBIA_ZONE = ZoneId.of("America/Bogota");

    private final EmprendimientoListadoRepositoryPort emprendimientos;
    private final Clock clock;

    public ListarEmprendimientosUseCase(
            EmprendimientoListadoRepositoryPort emprendimientos,
            Clock clock
    ) {
        this.emprendimientos = Objects.requireNonNull(emprendimientos, "emprendimientos");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public EmprendimientosPagina listar(ListarEmprendimientosQuery query) {
        if (query == null || query.filtro() == null) {
            throw validationError("query", "Los filtros de consulta son obligatorios.");
        }
        validate(query.filtro(), query.page(), query.size());

        FiltroEmprendimientos filtro = query.filtro();
        long offset = (long) query.page() * query.size();
        List<EmprendimientoListadoItem> items = emprendimientos
                .buscar(filtro, offset, query.size())
                .stream()
                .map(this::toItem)
                .toList();
        return new EmprendimientosPagina(
                items,
                query.page(),
                query.size(),
                emprendimientos.contar(filtro)
        );
    }

    private EmprendimientoListadoItem toItem(EmprendimientoListadoProjection projection) {
        LocalTime horaActual = LocalTime.now(clock.withZone(COLOMBIA_ZONE));
        return new EmprendimientoListadoItem(
                projection.id(),
                projection.nombre(),
                projection.categoria(),
                projection.tipoVendedor(),
                HorarioAtencion.estaAbierto(
                        projection.horaApertura(),
                        projection.horaCierre(),
                        horaActual
                ),
                projection.horaApertura().toString(),
                projection.horaCierre().toString(),
                projection.distanciaMetros(),
                new EmprendimientoListadoItem.UbicacionListado(
                        projection.latitud(),
                        projection.longitud(),
                        projection.direccion()
                )
        );
    }

    private void validate(FiltroEmprendimientos filtro, int page, int size) {
        if (page < 0) {
            throw validationError("page", "La página debe ser mayor o igual a cero.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw validationError("size", "El tamaño debe estar entre 1 y 100.");
        }
        boolean hasLatitude = filtro.latitud() != null;
        boolean hasLongitude = filtro.longitud() != null;
        if (hasLatitude != hasLongitude) {
            throw validationError("lat", "lat y lng deben enviarse juntos.");
        }
        if (hasLatitude && (!Double.isFinite(filtro.latitud())
                || filtro.latitud() < -90
                || filtro.latitud() > 90)) {
            throw validationError("lat", "La latitud debe estar entre -90 y 90.");
        }
        if (hasLongitude && (!Double.isFinite(filtro.longitud())
                || filtro.longitud() < -180
                || filtro.longitud() > 180)) {
            throw validationError("lng", "La longitud debe estar entre -180 y 180.");
        }
        if (filtro.radioMetros() != null && filtro.radioMetros() < 0) {
            throw validationError("radioMetros", "El radio debe ser mayor o igual a cero.");
        }
        if ((filtro.ordenarPorCercania() || filtro.radioMetros() != null)
                && !filtro.tieneCoordenadas()) {
            throw validationError("lat", "lat y lng son obligatorios para ordenar o filtrar por cercanía.");
        }
        if (filtro.campoOrden() == null || filtro.direccionOrden() == null) {
            throw validationError("sort", "El orden solicitado no es válido.");
        }
    }

    private DomainException validationError(String field, String message) {
        return new DomainException("VALIDATION_ERROR", message, Map.of(
                "fields",
                Map.of(field, message)
        ));
    }
}
