package co.edu.napa.pagos.infrastructure.adapters.in.web;

import co.edu.napa.pagos.application.ports.in.ConfigurarPagoVendedorPort;
import co.edu.napa.pagos.application.ports.in.ConsultarConfiguracionPagoPort;
import co.edu.napa.pagos.application.ports.in.ConsultarMediosPagoPort;
import co.edu.napa.pagos.application.ports.in.ConsultarMediosPagoPort.MediosDisponibles;
import co.edu.napa.pagos.domain.model.ConfiguracionPago;
import co.edu.napa.pagos.domain.model.CuentaBancaria;
import co.edu.napa.pagos.domain.model.MedioPago;
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

@RestController
@RequestMapping("/api/v1/vendedores/me/configuracion-pago")
public class ConfiguracionPagoController {
    private static final Set<String> REQUEST_FIELDS = Set.of("aceptaEfectivo", "cuentaBancaria");
    private static final Set<String> ACCOUNT_FIELDS = Set.of("banco", "tipoCuenta", "numeroCuenta");
    private static final String DOMICILIO_NOTICE = "El domicilio siempre exige cuenta bancaria registrada.";

    private final ConfigurarPagoVendedorPort configurar;
    private final ConsultarConfiguracionPagoPort consultar;
    private final ConsultarMediosPagoPort consultarMedios;

    public ConfiguracionPagoController(
            ConfigurarPagoVendedorPort configurar,
            ConsultarConfiguracionPagoPort consultar,
            ConsultarMediosPagoPort consultarMedios
    ) {
        this.configurar = Objects.requireNonNull(configurar, "configurar");
        this.consultar = Objects.requireNonNull(consultar, "consultar");
        this.consultarMedios = Objects.requireNonNull(consultarMedios, "consultarMedios");
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_VENDEDOR_AMBULANTE', 'ROLE_VENDEDOR_PUNTO_FIJO')")
    public ResponseEntity<Map<String, Object>> consultar(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request
    ) {
        rejectQueryParameters(request);
        return response(UUID.fromString(jwt.getSubject()));
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('ROLE_VENDEDOR_AMBULANTE', 'ROLE_VENDEDOR_PUNTO_FIJO')")
    public ResponseEntity<Map<String, Object>> configurar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request
    ) {
        rejectQueryParameters(request);
        if (body == null) {
            throw invalid("body", "El cuerpo debe ser un objeto JSON.");
        }
        rejectUnknownFields(body, REQUEST_FIELDS);
        Object acceptsCashValue = body.get("aceptaEfectivo");
        if (!(acceptsCashValue instanceof Boolean acceptsCash)) {
            throw invalid("aceptaEfectivo", "Indica si aceptas pagos en efectivo.");
        }
        configurar.configurar(
                UUID.fromString(jwt.getSubject()),
                acceptsCash,
                parseAccount(body.get("cuentaBancaria"))
        );
        return response(UUID.fromString(jwt.getSubject()));
    }

    private ResponseEntity<Map<String, Object>> response(UUID vendedorId) {
        ConfiguracionPago configuracion = consultar.consultar(vendedorId);
        MediosDisponibles medios = consultarMedios.consultar(vendedorId);
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("aceptaEfectivo", configuracion.aceptaEfectivo());
        body.put("cuentaBancaria", accountResponse(configuracion.cuentaBancaria()));
        body.put("mediosHabilitados", medios.mediosHabilitados());
        if (medios.vendedorPuntoFijo()) {
            body.put("domicilioDisponible", medios.domicilioDisponible());
            body.put("avisoDomicilio", DOMICILIO_NOTICE);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private CuentaBancaria parseAccount(Object account) {
        if (account == null) {
            return null;
        }
        if (!(account instanceof Map<?, ?> accountMap)) {
            throw invalid("cuentaBancaria", "La cuenta bancaria debe ser un objeto o null.");
        }
        rejectUnknownFields(accountMap, ACCOUNT_FIELDS);
        String bank = requiredText(accountMap, "banco");
        String accountType = requiredText(accountMap, "tipoCuenta");
        String accountNumber = requiredText(accountMap, "numeroCuenta");
        TipoCuentaBancaria type;
        try {
            type = TipoCuentaBancaria.valueOf(accountType);
        } catch (IllegalArgumentException exception) {
            throw invalid("cuentaBancaria.tipoCuenta", "El tipo debe ser AHORROS o CORRIENTE.");
        }
        return new CuentaBancaria(bank, type, accountNumber);
    }

    private String requiredText(Map<?, ?> object, String field) {
        Object value = object.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw invalid("cuentaBancaria." + field, "El campo es obligatorio.");
        }
        return text;
    }

    private void rejectUnknownFields(Map<?, ?> object, Set<String> allowedFields) {
        for (Object key : object.keySet()) {
            if (!(key instanceof String field) || !allowedFields.contains(field)) {
                throw invalid(String.valueOf(key), "La propiedad no está permitida.");
            }
        }
    }

    private void rejectQueryParameters(HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()) {
            throw invalid("query", "Este recurso no admite parámetros de consulta.");
        }
    }

    private Map<String, Object> accountResponse(CuentaBancaria account) {
        if (account == null) {
            return null;
        }
        return Map.of(
                "banco", account.banco(),
                "tipoCuenta", account.tipoCuenta(),
                "numeroCuenta", account.numeroCuenta()
        );
    }

    private DomainException invalid(String field, String message) {
        return new DomainException("VALIDATION_ERROR", message, Map.of(
                "fields", Map.of(field, message)
        ));
    }
}
