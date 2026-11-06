package co.edu.napa.emprendimientos.application.usecase;

import co.edu.napa.cuentas.domain.model.RolUsuario;
import co.edu.napa.emprendimientos.domain.model.CategoriaEmprendimiento;

import java.time.LocalTime;
import java.util.UUID;

public record CrearEmprendimientoCommand(
        UUID usuarioId,
        RolUsuario rolUsuario,
        String nombre,
        CategoriaEmprendimiento categoria,
        String descripcion,
        LocalTime horaApertura,
        LocalTime horaCierre,
        Double latitud,
        Double longitud,
        String direccion,
        String referencia
) {
}
