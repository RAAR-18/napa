package co.edu.napa.emprendimientos.application.usecase;

import co.edu.napa.emprendimientos.application.ports.in.ListarEmprendimientosPort;
import co.edu.napa.emprendimientos.application.ports.out.EmprendimientoRepositoryPort;
import co.edu.napa.emprendimientos.domain.model.CategoriaEmprendimiento;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

@Component
public class ListarEmprendimientosUseCase implements ListarEmprendimientosPort {
    private static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");
    private final EmprendimientoRepositoryPort emprendimientos;
    private final Clock clock;

    public ListarEmprendimientosUseCase(EmprendimientoRepositoryPort emprendimientos, Clock clock) {
        this.emprendimientos = Objects.requireNonNull(emprendimientos, "emprendimientos");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Resultado listar(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new co.edu.napa.shared.domain.DomainException(
                    "VALIDATION_ERROR", "La paginación debe usar page >= 0 y size entre 1 y 100."
            );
        }
        var result = emprendimientos.findActivos(PageRequest.of(page, size));
        LocalTime ahora = LocalTime.now(clock.withZone(ZONA_COLOMBIA));
        return new Resultado(
                result.getContent().stream()
                        .map(row -> new Item(
                                row.getId(),
                                row.getNombre(),
                                CategoriaEmprendimiento.valueOf(row.getCategoria()),
                                row.getTipoVendedor(),
                                row.getDescripcion(),
                                row.getImagenUrl(),
                                row.getHoraApertura(),
                                row.getHoraCierre(),
                                !ahora.isBefore(row.getHoraApertura()) && ahora.isBefore(row.getHoraCierre()),
                                row.getLatitud(),
                                row.getLongitud(),
                                row.getDireccion(),
                                row.getReferencia()
                        ))
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements()
        );
    }
}
