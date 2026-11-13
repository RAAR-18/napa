package co.edu.napa.emprendimientos.application.usecase;

import co.edu.napa.emprendimientos.application.ports.in.ConsultarEmprendimientoPort;
import co.edu.napa.emprendimientos.application.ports.out.EmprendimientoRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
public class ConsultarEmprendimientoUseCase implements ConsultarEmprendimientoPort {
    private final EmprendimientoRepositoryPort repository;

    public ConsultarEmprendimientoUseCase(EmprendimientoRepositoryPort repository) {
        this.repository = repository;
    }

    public Optional<Referencia> activoDelVendedor(UUID usuarioId) {
        return repository.findActiveByUsuarioId(usuarioId)
                .map(value -> new Referencia(value.id(), value.usuarioId(), value.nombre()));
    }

    public Optional<Referencia> activoPorId(UUID id) {
        return repository.findActiveById(id)
                .map(value -> new Referencia(value.id(), value.usuarioId(), value.nombre()));
    }
}
