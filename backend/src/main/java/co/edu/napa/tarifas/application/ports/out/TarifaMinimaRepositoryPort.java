package co.edu.napa.tarifas.application.ports.out;

import co.edu.napa.tarifas.domain.model.TarifaMinima;

import java.util.Optional;
import java.util.UUID;

public interface TarifaMinimaRepositoryPort {
    Optional<TarifaMinima> vigente();

    TarifaMinima registrarSiCambio(long valor, UUID administradorId, long valorDefecto);
}
