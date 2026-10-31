package co.edu.napa.emprendimientos.application.ports.in;

import co.edu.napa.cuentas.domain.model.RolUsuario;
import co.edu.napa.emprendimientos.domain.model.CategoriaEmprendimiento;
import co.edu.napa.emprendimientos.domain.model.Emprendimiento;

import java.time.LocalTime;
import java.util.UUID;

public interface CrearEmprendimientoPort {
    Emprendimiento crear(
            UUID usuarioId,
            String nombre,
            CategoriaEmprendimiento categoria,
            String descripcion,
            String imagenUrl,
            LocalTime horaApertura,
            LocalTime horaCierre,
            UbicacionInput ubicacion
    );

    record UbicacionInput(
            double latitud,
            double longitud,
            String direccion,
            String referencia
    ) {
    }
}
