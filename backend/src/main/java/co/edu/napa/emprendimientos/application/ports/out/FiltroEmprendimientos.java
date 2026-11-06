package co.edu.napa.emprendimientos.application.ports.out;

import co.edu.napa.emprendimientos.domain.model.TipoVendedor;

public record FiltroEmprendimientos(
        TipoVendedor tipoVendedor,
        Double latitud,
        Double longitud,
        Integer radioMetros,
        boolean ordenarPorCercania,
        CampoOrdenEmprendimientos campoOrden,
        DireccionOrdenEmprendimientos direccionOrden
) {
    public boolean tieneCoordenadas() {
        return latitud != null && longitud != null;
    }
}
