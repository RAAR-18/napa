package co.edu.napa.tarifas.application.ports.in;

import co.edu.napa.tarifas.domain.model.TarifaMinima;

import java.util.UUID;

public interface ActualizarTarifaMinimaPort {
    TarifaMinima actualizar(UUID administradorId, long valor);
}
