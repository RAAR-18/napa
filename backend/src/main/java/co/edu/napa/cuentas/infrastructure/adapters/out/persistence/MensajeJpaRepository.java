package co.edu.napa.cuentas.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MensajeJpaRepository extends JpaRepository<MensajeJpaEntity, UUID> {
}