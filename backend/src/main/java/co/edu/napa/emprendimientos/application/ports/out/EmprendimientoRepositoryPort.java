package co.edu.napa.emprendimientos.application.ports.out;

import co.edu.napa.emprendimientos.domain.model.Emprendimiento;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EmprendimientoRepositoryPort {
    Emprendimiento save(Emprendimiento emprendimiento);

    Optional<Emprendimiento> findActivoByUsuarioId(UUID usuarioId);

    Page<EmprendimientoListRow> findActivos(Pageable pageable);

    interface EmprendimientoListRow {
        UUID getId();
        String getNombre();
        String getCategoria();
        String getTipoVendedor();
        String getDescripcion();
        String getImagenUrl();
        java.time.LocalTime getHoraApertura();
        java.time.LocalTime getHoraCierre();
        double getLatitud();
        double getLongitud();
        String getDireccion();
        String getReferencia();
    }
}
