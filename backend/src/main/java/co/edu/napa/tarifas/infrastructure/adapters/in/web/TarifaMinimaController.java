package co.edu.napa.tarifas.infrastructure.adapters.in.web;

import co.edu.napa.tarifas.application.ports.in.ActualizarTarifaMinimaPort;
import co.edu.napa.tarifas.application.ports.in.ConsultarTarifaMinimaPort;
import co.edu.napa.tarifas.domain.model.TarifaMinima;
import co.edu.napa.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/tarifa-minima")
@Validated
public class TarifaMinimaController {
    private final ConsultarTarifaMinimaPort consultar;
    private final ActualizarTarifaMinimaPort actualizar;

    public TarifaMinimaController(
            ConsultarTarifaMinimaPort consultar,
            ActualizarTarifaMinimaPort actualizar
    ) {
        this.consultar = Objects.requireNonNull(consultar, "consultar");
        this.actualizar = Objects.requireNonNull(actualizar, "actualizar");
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")
    public ResponseEntity<TarifaMinimaResponse> consultar(HttpServletRequest request) {
        rejectQueryParameters(request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(TarifaMinimaResponse.from(consultar.consultar()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")
    public ResponseEntity<TarifaMinimaResponse> actualizar(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ActualizarTarifaMinimaRequest body,
            HttpServletRequest request
    ) {
        rejectQueryParameters(request);
        if (body.valor().compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
            String message = "La tarifa mínima supera el valor permitido.";
            throw new DomainException("VALIDATION_ERROR", message, Map.of(
                    "fields",
                    Map.of("valor", message)
            ));
        }
        TarifaMinima tarifa = actualizar.actualizar(
                java.util.UUID.fromString(jwt.getSubject()),
                body.valor().longValueExact()
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(TarifaMinimaResponse.from(tarifa));
    }

    private void rejectQueryParameters(HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()) {
            throw new DomainException("VALIDATION_ERROR", "Este recurso no admite parámetros de consulta.");
        }
    }

    public record ActualizarTarifaMinimaRequest(
            @NotNull @Positive @Digits(integer = 19, fraction = 0) BigDecimal valor
    ) {
    }

    public record TarifaMinimaResponse(long valor, java.time.Instant createdAt) {
        private static TarifaMinimaResponse from(TarifaMinima tarifa) {
            return new TarifaMinimaResponse(tarifa.valor(), tarifa.createdAt());
        }
    }
}
