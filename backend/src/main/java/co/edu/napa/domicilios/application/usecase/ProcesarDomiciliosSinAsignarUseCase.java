package co.edu.napa.domicilios.application.usecase;

import co.edu.napa.domicilios.application.PlazosDomicilio;
import co.edu.napa.domicilios.application.ports.in.ProcesarDomiciliosSinAsignarPort;
import co.edu.napa.domicilios.application.ports.out.DomicilioRepositoryPort;
import co.edu.napa.domicilios.domain.event.Actores;
import co.edu.napa.domicilios.domain.event.DomicilioCanceladoEvent;
import co.edu.napa.domicilios.domain.event.DomicilioSinAsignarEvent;
import co.edu.napa.domicilios.domain.model.Domicilio;
import co.edu.napa.domicilios.domain.model.EventoTrazabilidad;
import co.edu.napa.pedidos.application.ports.in.PedidoParaDomicilioPort;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * RF-14 FR-009. Procesa un lote por llamada: primero cancela los que superaron el plazo máximo y luego avisa a los
 * que superaron el plazo de aviso. Los bloqueos se saltan las filas ocupadas, así que varias instancias pueden
 * ejecutarlo a la vez sin duplicar avisos ni cancelaciones.
 */
public class ProcesarDomiciliosSinAsignarUseCase implements ProcesarDomiciliosSinAsignarPort {
    static final int LOTE = 50;

    private final DomicilioRepositoryPort domicilios;
    private final PedidoParaDomicilioPort pedidos;
    private final EventPublisherPort eventos;
    private final PlazosDomicilio plazos;
    private final Clock clock;

    public ProcesarDomiciliosSinAsignarUseCase(
            DomicilioRepositoryPort domicilios,
            PedidoParaDomicilioPort pedidos,
            EventPublisherPort eventos,
            PlazosDomicilio plazos,
            Clock clock
    ) {
        this.domicilios = Objects.requireNonNull(domicilios, "domicilios");
        this.pedidos = Objects.requireNonNull(pedidos, "pedidos");
        this.eventos = Objects.requireNonNull(eventos, "eventos");
        this.plazos = Objects.requireNonNull(plazos, "plazos");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public Resultado procesar() {
        Instant now = Instant.now(clock);
        int cancelados = 0;
        for (Domicilio domicilio : domicilios.bloquearDisponiblesPublicadosAntesDe(
                now.minus(plazos.cancelacionSinAsignar()), LOTE)) {
            Domicilio cancelado = domicilio.cancelarPorFaltaDeAsignacion(now);
            domicilios.actualizar(cancelado);
            domicilios.registrarEvento(EventoTrazabilidad.de(cancelado, null));
            pedidos.cancelarPorDomicilio(cancelado.pedidoId());
            eventos.publish(DomicilioCanceladoEvent.de(cancelado, null, Actores.SISTEMA));
            cancelados++;
        }
        int avisados = 0;
        for (Domicilio domicilio : domicilios.bloquearSinAvisoPublicadosAntesDe(
                now.minus(plazos.avisoSinAsignar()), LOTE)) {
            domicilios.actualizar(domicilio.marcarAvisoSinAsignar(now));
            eventos.publish(DomicilioSinAsignarEvent.de(domicilio, now));
            avisados++;
        }
        return new Resultado(avisados, cancelados);
    }
}
