package co.edu.napa.notificaciones.infrastructure.web;

import co.edu.napa.notificaciones.application.ports.out.NotificationPage;
import co.edu.napa.notificaciones.application.usecase.NotificationUseCase;
import co.edu.napa.notificaciones.domain.model.Notificacion;
import co.edu.napa.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notificaciones")
@Validated
public class NotificationController {
    private static final Set<String> LIST_PARAMETERS = Set.of("page", "size", "sort", "leida");

    private final NotificationUseCase notifications;

    public NotificationController(NotificationUseCase notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public ResponseEntity<NotificationListResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) Boolean leida,
            HttpServletRequest request
    ) {
        rejectUnknownListParameters(request);
        NotificationPage result = notifications.listar(
                UUID.fromString(jwt.getSubject()),
                leida,
                page,
                size,
                sort
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new NotificationListResponse(
                        result.content().stream().map(NotificationResponse::from).toList(),
                        result.page(),
                        result.size(),
                        result.totalElements()
                ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationResponse> open(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(NotificationResponse.from(notifications.abrir(UUID.fromString(jwt.getSubject()), id)));
    }

    @GetMapping("/preferencias")
    public ResponseEntity<PreferencesResponse> listPreferences(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request
    ) {
        rejectNoQueryParameters(request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new PreferencesResponse(notifications.listarPreferencias(UUID.fromString(jwt.getSubject()))));
    }

    @PatchMapping("/preferencias")
    public ResponseEntity<PreferencesResponse> updatePreferences(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PreferencesRequest request,
            HttpServletRequest servletRequest
    ) {
        rejectNoQueryParameters(servletRequest);
        List<NotificationUseCase.PreferenceUpdate> updates = request.preferencias().stream()
                .map(preference -> new NotificationUseCase.PreferenceUpdate(
                        preference.tipo(),
                        preference.habilitada()
                ))
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new PreferencesResponse(notifications.actualizarPreferencias(
                        UUID.fromString(jwt.getSubject()),
                        updates
                )));
    }

    private void rejectUnknownListParameters(HttpServletRequest request) {
        boolean unknown = request.getParameterMap().keySet().stream()
                .anyMatch(parameter -> !LIST_PARAMETERS.contains(parameter));
        if (unknown) {
            throw new DomainException("VALIDATION_ERROR", "La consulta contiene un parámetro no permitido.");
        }
    }

    private void rejectNoQueryParameters(HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()) {
            throw new DomainException("VALIDATION_ERROR", "Este recurso no admite parámetros de consulta.");
        }
    }

    public record NotificationListResponse(
            List<NotificationResponse> content,
            int page,
            int size,
            long totalElements
    ) {
        public NotificationListResponse {
            content = List.copyOf(content);
        }
    }

    public record NotificationResponse(
            UUID id,
            String tipo,
            String titulo,
            String cuerpo,
            boolean leida,
            Instant leidaAt,
            Instant createdAt
    ) {
        private static NotificationResponse from(Notificacion notification) {
            return new NotificationResponse(
                    notification.id(),
                    notification.tipo(),
                    notification.titulo(),
                    notification.cuerpo(),
                    notification.leidaAt() != null,
                    notification.leidaAt(),
                    notification.createdAt()
            );
        }
    }

    public record PreferencesRequest(@NotNull List<@Valid PreferenceRequest> preferencias) {
    }

    public record PreferenceRequest(
            @NotBlank String tipo,
            @NotNull Boolean habilitada
    ) {
    }

    public record PreferencesResponse(List<NotificationUseCase.PreferenceStatus> preferencias) {
        public PreferencesResponse {
            preferencias = List.copyOf(preferencias);
        }
    }
}
