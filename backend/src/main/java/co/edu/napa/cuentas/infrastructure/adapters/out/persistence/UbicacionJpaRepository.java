package co.edu.napa.cuentas.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UbicacionJpaRepository extends JpaRepository<UbicacionJpaEntity, UUID> {
}
