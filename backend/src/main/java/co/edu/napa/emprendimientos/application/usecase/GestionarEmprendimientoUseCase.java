package co.edu.napa.emprendimientos.application.usecase;

import co.edu.napa.cuentas.domain.model.RolUsuario;
import co.edu.napa.emprendimientos.application.ports.in.CrearEmprendimientoResult;
import co.edu.napa.emprendimientos.application.ports.in.GestionarEmprendimientoPort;
import co.edu.napa.emprendimientos.application.ports.out.EmprendimientoRepositoryPort;
import co.edu.napa.emprendimientos.domain.model.Emprendimiento;
import co.edu.napa.pedidos.application.ports.in.ConsultarPedidosSinCerrarPort;
import co.edu.napa.shared.application.ports.out.UbicacionRepositoryPort;
import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.domain.geo.Ubicacion;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class GestionarEmprendimientoUseCase implements GestionarEmprendimientoPort {
    static final String SIN_EMPRENDIMIENTO = "Aún no tienes un emprendimiento activo.";

    private final EmprendimientoRepositoryPort emprendimientos;
    private final UbicacionRepositoryPort ubicaciones;
    private final ConsultarPedidosSinCerrarPort pedidos;
    private final Clock clock;

    public GestionarEmprendimientoUseCase(
            EmprendimientoRepositoryPort emprendimientos,
            UbicacionRepositoryPort ubicaciones,
            ConsultarPedidosSinCerrarPort pedidos,
            Clock clock
    ) {
        this.emprendimientos = Objects.requireNonNull(emprendimientos, "emprendimientos");
        this.ubicaciones = Objects.requireNonNull(ubicaciones, "ubicaciones");
        this.pedidos = Objects.requireNonNull(pedidos, "pedidos");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional(readOnly = true)
    public CrearEmprendimientoResult consultarPropio(UUID vendedorId, RolUsuario rol) {
        Emprendimiento emprendimiento = activo(vendedorId);
        return new CrearEmprendimientoResult(emprendimiento, ubicacion(emprendimiento), rol);
    }

    @Override
    @Transactional
    public CrearEmprendimientoResult editar(UUID vendedorId, RolUsuario rol, Edicion edicion) {
        if (edicion == null || edicion.vacia()) {
            throw validation("emprendimiento", "Modifica al menos un campo.");
        }
        if (edicion.descripcion() != null
                && (edicion.descripcion().isBlank() || edicion.descripcion().strip().length() > 500)) {
            throw validation("descripcion", "La descripción es obligatoria y no debe exceder 500 caracteres.");
        }
        Emprendimiento actual = activo(vendedorId);
        Instant now = Instant.now(clock);
        Emprendimiento editado = actual.editar(edicion.descripcion(), edicion.horaApertura(),
                edicion.horaCierre(), now);
        Ubicacion ubicacion = ubicacion(actual);
        if (edicion.ubicacion() != null) {
            // El ambulante cambia de zona durante la jornada; el punto fijo no puede moverse con pedidos abiertos,
            // para que ningún cliente ni domiciliario llegue a un lugar equivocado.
            if (rol == RolUsuario.VENDEDOR_PUNTO_FIJO) {
                long abiertos = pedidos.contarDeEmprendimiento(actual.id());
                if (abiertos > 0) {
                    throw new DomainException("HAS_PENDING_ORDERS",
                            "No puedes cambiar tu ubicación mientras tengas pedidos pendientes.",
                            Map.of("pedidosPendientes", abiertos));
                }
            }
            ubicacion = ubicaciones.save(nuevaUbicacion(ubicacion, edicion.ubicacion(), rol, now));
        }
        return new CrearEmprendimientoResult(emprendimientos.save(editado), ubicacion, rol);
    }

    @Override
    @Transactional
    public void eliminar(UUID vendedorId) {
        Emprendimiento actual = activo(vendedorId);
        long abiertos = pedidos.contarDeEmprendimiento(actual.id());
        if (abiertos > 0) {
            throw new DomainException("HAS_PENDING_ORDERS",
                    "Resuelve tus pedidos pendientes antes de eliminar tu emprendimiento.",
                    Map.of("pedidosPendientes", abiertos));
        }
        emprendimientos.save(actual.eliminar(Instant.now(clock)));
    }

    private Emprendimiento activo(UUID vendedorId) {
        if (vendedorId == null) {
            throw new DomainException("UNAUTHORIZED", "No se pudo identificar al usuario autenticado.");
        }
        return emprendimientos.findActiveByUsuarioId(vendedorId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", SIN_EMPRENDIMIENTO));
    }

    private Ubicacion ubicacion(Emprendimiento emprendimiento) {
        return ubicaciones.findById(emprendimiento.ubicacionId())
                .orElseThrow(() -> new IllegalStateException("El emprendimiento no tiene ubicación."));
    }

    /** Misma regla que la creación (RF-28): coordenadas válidas y dirección obligatoria en punto fijo. */
    private Ubicacion nuevaUbicacion(Ubicacion actual, NuevaUbicacion nueva, RolUsuario rol, Instant now) {
        double latitud = coordenada(nueva.latitud(), "ubicacion.latitud", -90, 90);
        double longitud = coordenada(nueva.longitud(), "ubicacion.longitud", -180, 180);
        String direccion = texto(nueva.direccion(), "ubicacion.direccion");
        if (rol == RolUsuario.VENDEDOR_PUNTO_FIJO && direccion == null) {
            throw validation("ubicacion.direccion", "La dirección es obligatoria para un vendedor de punto fijo.");
        }
        return new Ubicacion(actual.id(), latitud, longitud, direccion, texto(nueva.referencia(), "ubicacion.referencia"),
                actual.barrio(), actual.createdAt(), now);
    }

    private static double coordenada(Double value, String field, double min, double max) {
        if (value == null || !Double.isFinite(value) || value < min || value > max) {
            throw validation(field, "La coordenada debe estar entre " + min + " y " + max + ".");
        }
        return value;
    }

    private static String texto(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > 300) {
            throw validation(field, "El campo no debe exceder 300 caracteres.");
        }
        return normalized;
    }

    private static DomainException validation(String field, String message) {
        return new DomainException("VALIDATION_ERROR", message, Map.of("fields", Map.of(field, message)));
    }
}
