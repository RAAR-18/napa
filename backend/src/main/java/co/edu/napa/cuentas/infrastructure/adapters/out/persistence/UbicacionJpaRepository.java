package co.edu.napa.cuentas.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UbicacionJpaRepository extends JpaRepository<UbicacionJpaEntity, UUID> {
}
