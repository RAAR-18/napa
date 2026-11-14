package co.edu.napa.emprendimientos.application.usecase;

import co.edu.napa.emprendimientos.application.ports.in.ConsultarEmprendimientoParaPedidoPort;
import co.edu.napa.emprendimientos.application.ports.out.EmprendimientoRepositoryPort;
import co.edu.napa.shared.application.ports.out.UbicacionRepositoryPort;
import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.domain.geo.GeoPoint;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class ConsultarEmprendimientoParaPedidoUseCase implements ConsultarEmprendimientoParaPedidoPort {
    private final EmprendimientoRepositoryPort emprendimientos;
    private final UbicacionRepositoryPort ubicaciones;

    public ConsultarEmprendimientoParaPedidoUseCase(
            EmprendimientoRepositoryPort emprendimientos,
            UbicacionRepositoryPort ubicaciones
    ) {
        this.emprendimientos = Objects.requireNonNull(emprendimientos, "emprendimientos");
        this.ubicaciones = Objects.requireNonNull(ubicaciones, "ubicaciones");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Referencia> consultar(UUID emprendimientoId) {
        Objects.requireNonNull(emprendimientoId, "emprendimientoId");
        return emprendimientos.findActiveById(emprendimientoId).map(value -> {
            var ubicacion = ubicaciones.findById(value.ubicacionId())
                    .orElseThrow(() -> new DomainException("INVALID_STATE",
                            "El emprendimiento no tiene una ubicación válida."));
            return new Referencia(value.id(), value.usuarioId(), value.nombre(),
                    new GeoPoint(ubicacion.latitud(), ubicacion.longitud()), ubicacion.id());
        });
    }
}
