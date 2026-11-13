package co.edu.napa.emprendimientos.application.ports.in;

import co.edu.napa.emprendimientos.domain.model.CategoriaEmprendimiento;
import co.edu.napa.emprendimientos.domain.model.TipoVendedor;

import java.util.UUID;

public record EmprendimientoListadoItem(
        UUID id,
        String nombre,
        CategoriaEmprendimiento categoria,
        TipoVendedor tipoVendedor,
        boolean abierto,
        String horaApertura,
        String horaCierre,
        Double distanciaMetros,
        UbicacionListado ubicacion
) {
    public record UbicacionListado(double latitud, double longitud, String direccion) {
    }
}
