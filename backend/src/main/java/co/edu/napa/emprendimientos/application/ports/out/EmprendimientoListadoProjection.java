package co.edu.napa.emprendimientos.application.ports.out;

import co.edu.napa.emprendimientos.domain.model.CategoriaEmprendimiento;
import co.edu.napa.emprendimientos.domain.model.TipoVendedor;

import java.time.LocalTime;
import java.util.UUID;

public record EmprendimientoListadoProjection(
        UUID id,
        String nombre,
        CategoriaEmprendimiento categoria,
        TipoVendedor tipoVendedor,
        LocalTime horaApertura,
        LocalTime horaCierre,
        double latitud,
        double longitud,
        String direccion,
        Double distanciaMetros
) {
}
