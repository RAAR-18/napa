package co.edu.napa.emprendimientos.application.ports.in;

import co.edu.napa.cuentas.domain.model.RolUsuario;

import java.time.LocalTime;
import java.util.UUID;

/** El vendedor consulta (RF-29), edita (RF-29) y elimina (RF-30) su emprendimiento activo. */
public interface GestionarEmprendimientoPort {
    CrearEmprendimientoResult consultarPropio(UUID vendedorId, RolUsuario rol);

    CrearEmprendimientoResult editar(UUID vendedorId, RolUsuario rol, Edicion edicion);

    void eliminar(UUID vendedorId);

    /** Campos opcionales; al menos uno debe venir. */
    record Edicion(String descripcion, LocalTime horaApertura, LocalTime horaCierre, NuevaUbicacion ubicacion) {
        public boolean vacia() {
            return descripcion == null && horaApertura == null && horaCierre == null && ubicacion == null;
        }
    }

    record NuevaUbicacion(Double latitud, Double longitud, String direccion, String referencia) {}
}
