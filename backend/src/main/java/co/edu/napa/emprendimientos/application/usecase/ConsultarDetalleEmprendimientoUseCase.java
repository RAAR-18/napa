package co.edu.napa.emprendimientos.application.usecase;

import co.edu.napa.cuentas.application.ports.in.ConsultarPerfilPublicoUsuarioPort;
import co.edu.napa.cuentas.application.ports.in.ConsultarPerfilPublicoUsuarioPort.PerfilPublico;
import co.edu.napa.emprendimientos.application.ports.in.ConsultarDetalleEmprendimientoPort;
import co.edu.napa.emprendimientos.application.ports.out.EmprendimientoRepositoryPort;
import co.edu.napa.emprendimientos.domain.model.Emprendimiento;
import co.edu.napa.emprendimientos.domain.model.HorarioAtencion;
import co.edu.napa.emprendimientos.domain.model.TipoVendedor;
import co.edu.napa.pagos.application.ports.in.ConsultarMediosPagoPort;
import co.edu.napa.shared.application.ports.out.UbicacionRepositoryPort;
import co.edu.napa.shared.domain.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Sin transacción propia: si pagos falla, su error no deja la lectura marcada para revertir. */
@Component
public class ConsultarDetalleEmprendimientoUseCase implements ConsultarDetalleEmprendimientoPort {
    static final String NO_DISPONIBLE = "El emprendimiento ya no está disponible.";
    private static final ZoneId COLOMBIA_ZONE = ZoneId.of("America/Bogota");
    private static final Logger LOG = LoggerFactory.getLogger(ConsultarDetalleEmprendimientoUseCase.class);

    private final EmprendimientoRepositoryPort emprendimientos;
    private final UbicacionRepositoryPort ubicaciones;
    private final ConsultarPerfilPublicoUsuarioPort usuarios;
    private final ConsultarMediosPagoPort mediosPago;
    private final Clock clock;

    public ConsultarDetalleEmprendimientoUseCase(
            EmprendimientoRepositoryPort emprendimientos,
            UbicacionRepositoryPort ubicaciones,
            ConsultarPerfilPublicoUsuarioPort usuarios,
            ConsultarMediosPagoPort mediosPago,
            Clock clock
    ) {
        this.emprendimientos = Objects.requireNonNull(emprendimientos, "emprendimientos");
        this.ubicaciones = Objects.requireNonNull(ubicaciones, "ubicaciones");
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.mediosPago = Objects.requireNonNull(mediosPago, "mediosPago");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Detalle consultar(UUID emprendimientoId) {
        Emprendimiento emprendimiento = activo(emprendimientoId);
        PerfilPublico vendedor = perfil(emprendimiento);
        TipoVendedor tipo = tipo(vendedor);
        var ubicacion = ubicaciones.findById(emprendimiento.ubicacionId())
                .orElseThrow(() -> new DomainException("NOT_FOUND", NO_DISPONIBLE));
        boolean ofreceDomicilio = tipo == TipoVendedor.PUNTO_FIJO && ofreceDomicilio(emprendimiento.usuarioId());
        List<String> modalidades = tipo == TipoVendedor.AMBULANTE
                ? List.of("ENTREGA_DIRECTA")
                : ofreceDomicilio ? List.of("RECOGIDA", "DOMICILIO") : List.of("RECOGIDA");
        return new Detalle(
                emprendimiento.id(),
                emprendimiento.nombre(),
                emprendimiento.categoria(),
                tipo,
                emprendimiento.descripcion(),
                emprendimiento.horaApertura().toString(),
                emprendimiento.horaCierre().toString(),
                HorarioAtencion.estaAbierto(emprendimiento.horaApertura(), emprendimiento.horaCierre(),
                        LocalTime.now(clock.withZone(COLOMBIA_ZONE))),
                new Ubicacion(ubicacion.latitud(), ubicacion.longitud(), ubicacion.direccion(), ubicacion.referencia()),
                modalidades,
                ofreceDomicilio
        );
    }

    @Override
    public Vendedor vendedor(UUID emprendimientoId) {
        PerfilPublico perfil = perfil(activo(emprendimientoId));
        return new Vendedor(perfil.nombre(), perfil.fotoUrl(), tipo(perfil),
                perfil.miembroDesde() == null ? null : perfil.miembroDesde().atZone(COLOMBIA_ZONE).toLocalDate());
    }

    private Emprendimiento activo(UUID emprendimientoId) {
        if (emprendimientoId == null) {
            throw new DomainException("NOT_FOUND", NO_DISPONIBLE);
        }
        return emprendimientos.findActiveById(emprendimientoId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", NO_DISPONIBLE));
    }

    private PerfilPublico perfil(Emprendimiento emprendimiento) {
        return usuarios.consultarPerfil(emprendimiento.usuarioId())
                .orElseThrow(() -> new DomainException("NOT_FOUND", NO_DISPONIBLE));
    }

    private static TipoVendedor tipo(PerfilPublico perfil) {
        return "VENDEDOR_PUNTO_FIJO".equals(perfil.rol()) ? TipoVendedor.PUNTO_FIJO : TipoVendedor.AMBULANTE;
    }

    /** Si pagos falla, el detalle se muestra igual, sin domicilio (RNF15). */
    private boolean ofreceDomicilio(UUID vendedorId) {
        try {
            return mediosPago.consultar(vendedorId).domicilioDisponible();
        } catch (RuntimeException exception) {
            LOG.warn("No se pudo consultar si el vendedor ofrece domicilio", exception);
            return false;
        }
    }
}
