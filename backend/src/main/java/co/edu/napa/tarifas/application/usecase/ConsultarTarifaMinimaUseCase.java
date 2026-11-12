package co.edu.napa.tarifas.application.usecase;

import co.edu.napa.tarifas.application.ports.in.ConsultarTarifaMinimaPort;
import co.edu.napa.tarifas.application.ports.out.TarifaMinimaDefectoPort;
import co.edu.napa.tarifas.application.ports.out.TarifaMinimaRepositoryPort;
import co.edu.napa.tarifas.domain.model.TarifaMinima;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Component
public class ConsultarTarifaMinimaUseCase implements ConsultarTarifaMinimaPort {
    private final TarifaMinimaRepositoryPort tarifas;
    private final TarifaMinimaDefectoPort tarifaDefecto;

    public ConsultarTarifaMinimaUseCase(
            TarifaMinimaRepositoryPort tarifas,
            TarifaMinimaDefectoPort tarifaDefecto
    ) {
        this.tarifas = Objects.requireNonNull(tarifas, "tarifas");
        this.tarifaDefecto = Objects.requireNonNull(tarifaDefecto, "tarifaDefecto");
    }

    @Override
    @Transactional(readOnly = true)
    public TarifaMinima consultar() {
        return tarifas.vigente()
                .orElseGet(() -> new TarifaMinima(tarifaDefecto.valor(), null));
    }
}
