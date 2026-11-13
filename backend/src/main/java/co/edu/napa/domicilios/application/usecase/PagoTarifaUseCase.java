package co.edu.napa.domicilios.application.usecase;

import co.edu.napa.domicilios.application.ports.in.PagoTarifaPort;
import co.edu.napa.domicilios.application.ports.out.DomicilioRepositoryPort;
import co.edu.napa.domicilios.domain.model.Domicilio;
import co.edu.napa.domicilios.domain.model.EstadoDomicilio;
import co.edu.napa.pagos.application.ports.in.PagoTarifaDomicilioPort;
import co.edu.napa.pagos.application.ports.in.PagoTarifaDomicilioPort.PagoTarifa;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * RF-43. Valida el domicilio, su estado y quién actúa, y bloquea el domicilio antes del pago (orden domicilio → pago).
 * El monto es la tarifa con la que se publicó el domicilio. A quien no es parte del domicilio se le responde que no
 * existe.
 */
public class PagoTarifaUseCase implements PagoTarifaPort {
    private static final String NO_ENCONTRADO = "No encontramos el domicilio.";
    private static final String MEDIO = "CUENTA_BANCARIA";

    private final DomicilioRepositoryPort domicilios;
    private final PagoTarifaDomicilioPort pagos;

    public PagoTarifaUseCase(DomicilioRepositoryPort domicilios, PagoTarifaDomicilioPort pagos) {
        this.domicilios = Objects.requireNonNull(domicilios, "domicilios");
        this.pagos = Objects.requireNonNull(pagos, "pagos");
    }

    @Override
    @Transactional
    public EstadoPagoTarifa registrar(UUID vendedorId, UUID domicilioId) {
        Objects.requireNonNull(vendedorId, "vendedorId");
        Domicilio domicilio = bloquear(domicilioId, value -> value.vendedorId().equals(vendedorId));
        if (domicilio.estado() != EstadoDomicilio.ENTREGADO) {
            throw new DomainException("INVALID_STATE", "Primero debe confirmarse la entrega.",
                    Map.of("estadoActual", domicilio.estado().name()));
        }
        PagoTarifa pago = pagos.registrar(domicilio.id(), domicilio.compradorId(), domicilio.vendedorId(),
                domicilio.domiciliarioId(), domicilio.tarifa());
        return vista(domicilio, pago, null);
    }

    @Override
    @Transactional
    public EstadoPagoTarifa confirmar(UUID domiciliarioId, UUID domicilioId) {
        Objects.requireNonNull(domiciliarioId, "domiciliarioId");
        Domicilio domicilio = bloquear(domicilioId, value -> value.asignadoA(domiciliarioId));
        return vista(domicilio, pagos.confirmar(domicilio.id()), null);
    }

    @Override
    @Transactional(readOnly = true)
    public EstadoPagoTarifa consultarComoVendedor(UUID vendedorId, UUID domicilioId) {
        Objects.requireNonNull(vendedorId, "vendedorId");
        Domicilio domicilio = buscar(domicilioId, value -> value.vendedorId().equals(vendedorId));
        CuentaDomiciliario cuenta = null;
        if (domicilio.estado() == EstadoDomicilio.ENTREGADO && domicilio.domiciliarioId() != null) {
            cuenta = pagos.cuentaDelDomiciliario(domicilio.domiciliarioId())
                    .map(value -> new CuentaDomiciliario(value.banco(), value.tipoCuenta(), value.numeroCuenta()))
                    .orElse(null);
        }
        return vista(domicilio, pagos.consultar(domicilio.id()).orElse(null), cuenta);
    }

    @Override
    @Transactional(readOnly = true)
    public EstadoPagoTarifa consultarComoDomiciliario(UUID domiciliarioId, UUID domicilioId) {
        Objects.requireNonNull(domiciliarioId, "domiciliarioId");
        Domicilio domicilio = buscar(domicilioId, value -> value.asignadoA(domiciliarioId));
        return vista(domicilio, pagos.consultar(domicilio.id()).orElse(null), null);
    }

    private Domicilio bloquear(UUID domicilioId, Predicate<Domicilio> participa) {
        return domicilios.bloquear(Objects.requireNonNull(domicilioId, "domicilioId"))
                .filter(participa)
                .orElseThrow(() -> new DomainException("NOT_FOUND", NO_ENCONTRADO));
    }

    private Domicilio buscar(UUID domicilioId, Predicate<Domicilio> participa) {
        return domicilios.buscar(Objects.requireNonNull(domicilioId, "domicilioId"))
                .filter(participa)
                .orElseThrow(() -> new DomainException("NOT_FOUND", NO_ENCONTRADO));
    }

    private static EstadoPagoTarifa vista(Domicilio domicilio, PagoTarifa pago, CuentaDomiciliario cuenta) {
        if (pago == null) {
            return new EstadoPagoTarifa(domicilio.id(), null, "SIN_REGISTRAR", domicilio.tarifa(), MEDIO, null, null,
                    cuenta);
        }
        return new EstadoPagoTarifa(domicilio.id(), pago.pagoId(), pago.estado(), pago.monto(), pago.medio(),
                pago.registradoAt(), pago.confirmadoAt(), cuenta);
    }
}
