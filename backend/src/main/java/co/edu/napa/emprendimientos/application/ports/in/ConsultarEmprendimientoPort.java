package co.edu.napa.emprendimientos.application.ports.in;

import java.util.Optional;
import java.util.UUID;

public interface ConsultarEmprendimientoPort {
    Optional<Referencia> activoDelVendedor(UUID usuarioId);
    Optional<Referencia> activoPorId(UUID id);

    record Referencia(UUID id, UUID usuarioId, String nombre) {}
}
