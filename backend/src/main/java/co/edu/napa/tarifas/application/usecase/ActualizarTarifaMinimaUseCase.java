package co.edu.napa.tarifas.application.usecase;

import co.edu.napa.tarifas.application.ports.in.ActualizarTarifaMinimaPort;
import co.edu.napa.tarifas.application.ports.out.TarifaMinimaDefectoPort;
import co.edu.napa.tarifas.application.ports.out.TarifaMinimaRepositoryPort;
import co.edu.napa.tarifas.domain.model.TarifaMinima;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Component
public class ActualizarTarifaMinimaUseCase implements ActualizarTarifaMinimaPort {
    private final TarifaMinimaRepositoryPort tarifas;
    private final TarifaMinimaDefectoPort tarifaDefecto;

    public ActualizarTarifaMinimaUseCase(
            TarifaMinimaRepositoryPort tarifas,
            TarifaMinimaDefectoPort tarifaDefecto
    ) {
        this.tarifas = Objects.requireNonNull(tarifas, "tarifas");
        this.tarifaDefecto = Objects.requireNonNull(tarifaDefecto, "tarifaDefecto");
    }

    @Override
    @Transactional
    public TarifaMinima actualizar(UUID administradorId, long valor) {
        Objects.requireNonNull(administradorId, "administradorId");
        TarifaMinima requested = new TarifaMinima(valor, null);
        return tarifas.registrarSiCambio(
                requested.valor(),
                administradorId,
                tarifaDefecto.valor()
        );
    }
}
