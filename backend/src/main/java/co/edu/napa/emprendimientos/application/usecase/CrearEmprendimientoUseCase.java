package co.edu.napa.emprendimientos.application.usecase;

import co.edu.napa.cuentas.application.ports.out.UbicacionRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import co.edu.napa.cuentas.domain.model.EstadoUsuario;
import co.edu.napa.cuentas.domain.model.RolUsuario;
import co.edu.napa.cuentas.domain.model.Ubicacion;
import co.edu.napa.cuentas.domain.model.Usuario;
import co.edu.napa.emprendimientos.application.ports.in.CrearEmprendimientoPort;
import co.edu.napa.emprendimientos.application.ports.out.EmprendimientoRepositoryPort;
import co.edu.napa.emprendimientos.domain.model.Emprendimiento;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Component
public class CrearEmprendimientoUseCase implements CrearEmprendimientoPort {
    private final UsuarioRepositoryPort usuarios;
    private final UbicacionRepositoryPort ubicaciones;
    private final EmprendimientoRepositoryPort emprendimientos;
    private final Clock clock;

    public CrearEmprendimientoUseCase(
            UsuarioRepositoryPort usuarios,
            UbicacionRepositoryPort ubicaciones,
            EmprendimientoRepositoryPort emprendimientos,
            Clock clock
    ) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.ubicaciones = Objects.requireNonNull(ubicaciones, "ubicaciones");
        this.emprendimientos = Objects.requireNonNull(emprendimientos, "emprendimientos");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public Emprendimiento crear(
            UUID usuarioId,
            String nombre,
            co.edu.napa.emprendimientos.domain.model.CategoriaEmprendimiento categoria,
            String descripcion,
            String imagenUrl,
            java.time.LocalTime horaApertura,
            java.time.LocalTime horaCierre,
            UbicacionInput ubicacionInput
    ) {
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new DomainException("UNAUTHORIZED", "La sesión no corresponde a un usuario válido."));
        if (usuario.estado() != EstadoUsuario.ACTIVA) {
            throw new DomainException("ACCOUNT_NOT_VERIFIED", "Debes verificar tu celular antes de crear un emprendimiento.");
        }
        RolUsuario rol = usuario.rol();
        if (rol != RolUsuario.VENDEDOR_AMBULANTE && rol != RolUsuario.VENDEDOR_PUNTO_FIJO) {
            throw new DomainException("FORBIDDEN", "Solo los vendedores pueden crear un emprendimiento.");
        }
        if (emprendimientos.findActivoByUsuarioId(usuarioId).isPresent()) {
            throw new DomainException("CONFLICT", "Ya tienes un emprendimiento activo.");
        }
        validarUbicacion(ubicacionInput);

        Instant now = Instant.now(clock);
        Ubicacion ubicacion = new Ubicacion(
                UUID.randomUUID(),
                ubicacionInput.latitud(),
                ubicacionInput.longitud(),
                ubicacionInput.direccion().trim(),
                ubicacionInput.referencia(),
                now,
                now
        );
        ubicaciones.save(ubicacion);
        return emprendimientos.save(Emprendimiento.crear(
                usuarioId,
                rol,
                ubicacion.id(),
                nombre,
                categoria,
                descripcion,
                imagenUrl,
                horaApertura,
                horaCierre,
                now
        ));
    }

    private void validarUbicacion(UbicacionInput ubicacion) {
        if (ubicacion == null
                || Double.isNaN(ubicacion.latitud())
                || Double.isNaN(ubicacion.longitud())
                || ubicacion.latitud() < -90 || ubicacion.latitud() > 90
                || ubicacion.longitud() < -180 || ubicacion.longitud() > 180) {
            throw new DomainException("VALIDATION_ERROR", "La ubicación debe tener coordenadas válidas.");
        }
        if (ubicacion.direccion() == null || ubicacion.direccion().isBlank()
                || ubicacion.direccion().trim().length() > 300) {
            throw new DomainException("VALIDATION_ERROR", "La dirección es obligatoria y no puede superar 300 caracteres.");
        }
    }
}
