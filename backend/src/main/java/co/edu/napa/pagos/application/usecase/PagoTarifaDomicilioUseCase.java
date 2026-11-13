package co.edu.napa.pagos.application.usecase;

import co.edu.napa.pagos.application.ports.in.PagoTarifaDomicilioPort;
import co.edu.napa.pagos.application.ports.out.ConfiguracionPagoRepositoryPort;
import co.edu.napa.pagos.application.ports.out.PagoRepositoryPort;
import co.edu.napa.pagos.domain.event.PagoTarifaRegistradoEvent;
import co.edu.napa.pagos.domain.model.Pago;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class PagoTarifaDomicilioUseCase implements PagoTarifaDomicilioPort {
    private final PagoRepositoryPort pagos;
    private final ConfiguracionPagoRepositoryPort configuraciones;
    private final EventPublisherPort eventos;
    private final Clock clock;

    public PagoTarifaDomicilioUseCase(
            PagoRepositoryPort pagos,
            ConfiguracionPagoRepositoryPort configuraciones,
            EventPublisherPort eventos,
            Clock clock
    ) {
        this.pagos = Objects.requireNonNull(pagos, "pagos");
        this.configuraciones = Objects.requireNonNull(configuraciones, "configuraciones");
        this.eventos = Objects.requireNonNull(eventos, "eventos");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public PagoTarifa registrar(UUID domicilioId, UUID compradorId, UUID vendedorId, UUID domiciliarioId, long monto) {
        Objects.requireNonNull(domicilioId, "domicilioId");
        if (pagos.bloquearTarifaPorDomicilio(domicilioId).isPresent()) {
            throw new DomainException("CONFLICT", "Ya registraste el pago de la tarifa.");
        }
        Pago pago = pagos.guardar(Pago.tarifaRegistrada(domicilioId, compradorId, vendedorId, domiciliarioId,
                monto, Instant.now(clock)));
        eventos.publish(PagoTarifaRegistradoEvent.de(pago));
        return vista(pago);
    }

    @Override
    @Transactional
    public PagoTarifa confirmar(UUID domicilioId) {
        Pago pago = pagos.bloquearTarifaPorDomicilio(Objects.requireNonNull(domicilioId, "domicilioId"))
                .orElseThrow(() -> new DomainException("FEE_NOT_REGISTERED", "El vendedor aún no registró el pago."));
        Pago confirmado = pago.confirmarTarifa(Instant.now(clock));
        pagos.actualizar(confirmado);
        return vista(confirmado);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PagoTarifa> consultar(UUID domicilioId) {
        return pagos.buscarTarifaPorDomicilio(Objects.requireNonNull(domicilioId, "domicilioId"))
                .map(PagoTarifaDomicilioUseCase::vista);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CuentaCobro> cuentaDelDomiciliario(UUID domiciliarioId) {
        return configuraciones.findByVendedorId(Objects.requireNonNull(domiciliarioId, "domiciliarioId"))
                .map(configuracion -> configuracion.cuentaBancaria())
                .map(cuenta -> new CuentaCobro(cuenta.banco(), cuenta.tipoCuenta().name(), cuenta.numeroCuenta()));
    }

    private static PagoTarifa vista(Pago pago) {
        return new PagoTarifa(pago.id(), pago.estado().name(), pago.monto(), pago.medio().name(), pago.createdAt(),
                pago.confirmadoAt());
    }
}
