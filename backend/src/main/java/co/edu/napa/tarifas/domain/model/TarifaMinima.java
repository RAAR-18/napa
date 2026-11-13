package co.edu.napa.tarifas.domain.model;

import co.edu.napa.shared.domain.DomainException;

import java.time.Instant;
import java.util.Map;

public record TarifaMinima(long valor, Instant createdAt) {
    public TarifaMinima {
        if (valor <= 0) {
            String message = "La tarifa mínima debe ser mayor a cero.";
            throw new DomainException("VALIDATION_ERROR", message, Map.of(
                    "fields",
                    Map.of("valor", message)
            ));
        }
    }
}
