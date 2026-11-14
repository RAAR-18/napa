package co.edu.napa.pagos.infrastructure.adapters.in.web;

import co.edu.napa.pagos.application.ports.in.ConfigurarPagoVendedorPort;
import co.edu.napa.pagos.application.ports.in.ConsultarConfiguracionPagoPort;
import co.edu.napa.pagos.domain.model.CuentaBancaria;
import co.edu.napa.pagos.domain.model.TipoCuentaBancaria;
import co.edu.napa.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * RF-43 (FR-008): cuenta bancaria del domiciliario, a la que el vendedor le transfiere la tarifa. Reutiliza la
 * configuración de pago cifrada de RF-63; el domiciliario no acepta efectivo. Se responde enmascarada.
 */
@RestController
@RequestMapping("/api/v1/domiciliarios/me/configuracion-pago")
public class CuentaDomiciliarioController {
    private static final Set<String> FIELDS = Set.of("banco", "tipoCuenta", "numeroCuenta");

    private final ConfigurarPagoVendedorPort configurar;
    private final ConsultarConfiguracionPagoPort consultar;

    public CuentaDomiciliarioController(ConfigurarPagoVendedorPort configurar, ConsultarConfiguracionPagoPort consultar) {
        this.configurar = Objects.requireNonNull(configurar, "configurar");
        this.consultar = Objects.requireNonNull(consultar, "consultar");
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_DOMICILIARIO')")
    public ResponseEntity<Map<String, Object>> consultar(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        rejectQuery(request);
        return response(UUID.fromString(jwt.getSubject()));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('ROLE_DOMICILIARIO')")
    public ResponseEntity<Map<String, Object>> guardar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request
    ) {
        rejectQuery(request);
        if (body == null) throw invalid("body", "El cuerpo debe ser un objeto JSON.");
        for (String key : body.keySet()) {
            if (!FIELDS.contains(key)) throw invalid(key, "La propiedad no está permitida.");
        }
        TipoCuentaBancaria tipo;
        try {
            tipo = TipoCuentaBancaria.valueOf(required(body, "tipoCuenta"));
        } catch (IllegalArgumentException exception) {
            throw invalid("tipoCuenta", "El tipo debe ser AHORROS o CORRIENTE.");
        }
        CuentaBancaria cuenta = new CuentaBancaria(required(body, "banco"), tipo, required(body, "numeroCuenta"));
        UUID usuarioId = UUID.fromString(jwt.getSubject());
        configurar.configurar(usuarioId, false, cuenta);
        return response(usuarioId);
    }

    private ResponseEntity<Map<String, Object>> response(UUID usuarioId) {
        CuentaBancaria cuenta = consultar.consultar(usuarioId).cuentaBancaria();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("banco", cuenta == null ? null : cuenta.banco());
        body.put("tipoCuenta", cuenta == null ? null : cuenta.tipoCuenta().name());
        body.put("numeroCuenta", cuenta == null ? null : enmascarar(cuenta.numeroCuenta()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    static String enmascarar(String numero) {
        int visibles = Math.min(4, numero.length());
        return "•".repeat(numero.length() - visibles) + numero.substring(numero.length() - visibles);
    }

    private static String required(Map<String, Object> body, String field) {
        if (!(body.get(field) instanceof String text) || text.isBlank()) {
            throw invalid(field, "El campo es obligatorio.");
        }
        return text;
    }

    private static void rejectQuery(HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()) {
            throw invalid("query", "Este recurso no admite parámetros de consulta.");
        }
    }

    private static DomainException invalid(String field, String message) {
        return new DomainException("VALIDATION_ERROR", message, Map.of("fields", Map.of(field, message)));
    }
}
