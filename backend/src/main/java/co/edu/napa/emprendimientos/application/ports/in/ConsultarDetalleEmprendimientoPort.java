package co.edu.napa.emprendimientos.application.ports.in;

import co.edu.napa.emprendimientos.domain.model.CategoriaEmprendimiento;
import co.edu.napa.emprendimientos.domain.model.TipoVendedor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Lo que ve el cliente: el detalle del emprendimiento (RF-32) y los datos públicos de su vendedor (RF-34). */
public interface ConsultarDetalleEmprendimientoPort {
    Detalle consultar(UUID emprendimientoId);

    Vendedor vendedor(UUID emprendimientoId);

    record Detalle(
            UUID id,
            String nombre,
            CategoriaEmprendimiento categoria,
            TipoVendedor tipoVendedor,
            String descripcion,
            String horaApertura,
            String horaCierre,
            boolean abierto,
            Ubicacion ubicacion,
            List<String> modalidadesEntrega,
            boolean ofreceDomicilio
    ) {
        public Detalle {
            modalidadesEntrega = List.copyOf(modalidadesEntrega);
        }
    }

    record Ubicacion(double latitud, double longitud, String direccion, String referencia) {}

    /** Sin celular, correo ni documento (RF-34 FR-002). */
    record Vendedor(String nombre, String fotoUrl, TipoVendedor tipoVendedor, LocalDate miembroDesde) {}
}
