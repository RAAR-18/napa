package co.edu.napa.domicilios.application.usecase;

import co.edu.napa.domicilios.application.ports.in.CancelarDomicilioAdminPort;
import co.edu.napa.domicilios.application.ports.out.DomicilioRepositoryPort;
import co.edu.napa.domicilios.application.ports.out.IntentosCodigoEntregaPort;
import co.edu.napa.domicilios.domain.event.DomicilioCanceladoEvent;
import co.edu.napa.domicilios.domain.model.Domicilio;
import co.edu.napa.domicilios.domain.model.EventoTrazabilidad;
import co.edu.napa.pedidos.application.ports.in.PedidoParaDomicilioPort;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * RF-13. En una transacción: el domicilio pasa a {@code CANCELADO} con el motivo, queda el evento de trazabilidad
 * con el administrador como autor, el pedido se cancela (y devuelve su inventario) y sale E15 para avisar a las
 * partes. El orden de bloqueo es domicilio → pedido, el mismo de la entrega (RF-24), así que si ambos llegan a la
 * vez el segundo ve el estado nuevo. El pago no se toca: el dinero se devuelve fuera de la plataforma.
 */
public class CancelarDomicilioAdminUseCase implements CancelarDomicilioAdminPort {
    private final DomicilioRepositoryPort domicilios;
    private final PedidoParaDomicilioPort pedidos;
    private final IntentosCodigoEntregaPort intentos;
    private final EventPublisherPort eventos;
    private final Clock clock;

    public CancelarDomicilioAdminUseCase(
            DomicilioRepositoryPort domicilios,
            PedidoParaDomicilioPort pedidos,
            IntentosCodigoEntregaPort intentos,
            EventPublisherPort eventos,
            Clock clock
    ) {
        this.domicilios = Objects.requireNonNull(domicilios, "domicilios");
        this.pedidos = Objects.requireNonNull(pedidos, "pedidos");
        this.intentos = Objects.requireNonNull(intentos, "intentos");
        this.eventos = Objects.requireNonNull(eventos, "eventos");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public Cancelacion cancelar(UUID administradorId, UUID domicilioId, String motivo) {
        Objects.requireNonNull(administradorId, "administradorId");
        Objects.requireNonNull(domicilioId, "domicilioId");
        // El motivo se valida antes de tocar la base.
        String limpio = Domicilio.motivoValido(motivo);
        Domicilio domicilio = domicilios.bloquear(domicilioId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "El domicilio no existe."));
        Domicilio cancelado = domicilio.cancelarPorAdministrador(limpio, Instant.now(clock));
        domicilios.actualizar(cancelado);
        domicilios.registrarEvento(EventoTrazabilidad.de(cancelado, administradorId));
        pedidos.cancelarPorDomicilio(cancelado.pedidoId());
        eventos.publish(DomicilioCanceladoEvent.de(cancelado, cancelado.domiciliarioId(), administradorId));
        try {
            intentos.limpiar(cancelado.id());
        } catch (RuntimeException ignored) {
            // Los contadores del código vencen solos; no deben impedir la cancelación.
        }
        return new Cancelacion(cancelado.id(), cancelado.estado().name(), cancelado.detalleCancelacion(),
                new Pedido(cancelado.pedidoId(), "CANCELADO"));
    }
}
