package co.edu.napa.emprendimientos.application.usecase;

import co.edu.napa.cuentas.domain.model.RolUsuario;
import co.edu.napa.emprendimientos.application.ports.in.CrearEmprendimientoPort;
import co.edu.napa.emprendimientos.application.ports.out.EmprendimientoRepositoryPort;
import co.edu.napa.emprendimientos.domain.model.Emprendimiento;
import co.edu.napa.emprendimientos.domain.model.EstadoEmprendimiento;
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
public class CrearEmprendimientoUseCase implements CrearEmprendimientoPort {
    private final EmprendimientoRepositoryPort emprendimientos;
    private final UbicacionRepositoryPort ubicaciones;
    private final Clock clock;

    public CrearEmprendimientoUseCase(
            EmprendimientoRepositoryPort emprendimientos,
            UbicacionRepositoryPort ubicaciones,
            Clock clock
    ) {
        this.emprendimientos = Objects.requireNonNull(emprendimientos, "emprendimientos");
        this.ubicaciones = Objects.requireNonNull(ubicaciones, "ubicaciones");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public CrearEmprendimientoResult crear(CrearEmprendimientoCommand command) {
        if (command == null) {
            throw new DomainException("VALIDATION_ERROR", "Los datos del emprendimiento son obligatorios.");
        }
        RolUsuario rol = command.rolUsuario();
        if (rol != RolUsuario.VENDEDOR_AMBULANTE && rol != RolUsuario.VENDEDOR_PUNTO_FIJO) {
            throw new DomainException("FORBIDDEN", "Solo un vendedor puede crear un emprendimiento.");
        }
        if (command.usuarioId() == null) {
            throw new DomainException("UNAUTHORIZED", "No se pudo identificar al usuario autenticado.");
        }
        if (command.categoria() == null) {
            throw validationError("categoria", "La categoría es obligatoria.");
        }
        if (command.horaApertura() == null) {
            throw validationError("horaApertura", "La hora de apertura es obligatoria.");
        }
        if (command.horaCierre() == null) {
            throw validationError("horaCierre", "La hora de cierre es obligatoria.");
        }

        String direccion = normalizeOptional(command.direccion(), "ubicacion.direccion");
        if (rol == RolUsuario.VENDEDOR_PUNTO_FIJO && direccion == null) {
            throw validationError("ubicacion.direccion", "La dirección es obligatoria para un vendedor de punto fijo.");
        }

        Instant now = Instant.now(clock);
        Ubicacion ubicacion = new Ubicacion(
                UUID.randomUUID(),
                requireCoordinate(command.latitud(), "ubicacion.latitud", -90, 90),
                requireCoordinate(command.longitud(), "ubicacion.longitud", -180, 180),
                direccion,
                normalizeOptional(command.referencia(), "ubicacion.referencia"),
                now,
                now
        );
        Emprendimiento emprendimiento = new Emprendimiento(
                UUID.randomUUID(),
                command.usuarioId(),
                ubicacion.id(),
                normalizeRequired(command.nombre(), "nombre"),
                command.categoria(),
                normalizeRequired(command.descripcion(), "descripcion"),
                command.horaApertura(),
                command.horaCierre(),
                EstadoEmprendimiento.ACTIVO,
                now,
                now
        );

        if (emprendimientos.existsActiveByUsuarioId(command.usuarioId())) {
            throw conflict();
        }

        Ubicacion savedLocation = ubicaciones.save(ubicacion);
        Emprendimiento savedBusiness = emprendimientos.save(emprendimiento);
        return new CrearEmprendimientoResult(savedBusiness, savedLocation, rol);
    }

    private double requireCoordinate(Double value, String field, double min, double max) {
        if (value == null || !Double.isFinite(value) || value < min || value > max) {
            throw validationError(field, "La coordenada debe estar entre " + min + " y " + max + ".");
        }
        return value;
    }

    private String normalizeRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw validationError(field, "El campo " + field + " es obligatorio.");
        }
        return value.strip();
    }

    private String normalizeOptional(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > 300) {
            throw validationError(field, "El campo " + field + " no debe exceder 300 caracteres.");
        }
        return normalized;
    }

    private DomainException validationError(String field, String message) {
        return new DomainException("VALIDATION_ERROR", message, Map.of(
                "fields",
                Map.of(field, message)
        ));
    }

    private DomainException conflict() {
        return new DomainException("CONFLICT", "Ya tienes un emprendimiento activo.");
    }
}
