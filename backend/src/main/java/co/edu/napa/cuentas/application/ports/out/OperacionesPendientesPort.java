package co.edu.napa.cuentas.application.ports.out;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * RF-11, RF-67 y RF-68: lo que el usuario tiene en curso. Pedidos sin cerrar (como cliente o vendedor), domicilios
 * sin entregar, tarifas de domicilio sin confirmar y un emprendimiento ACTIVO.
 */
public interface OperacionesPendientesPort {
    OperacionesPendientes contar(UUID usuarioId);

    record OperacionesPendientes(long pedidosPendientes, long domiciliosActivos, long tarifasPendientes,
                                 boolean emprendimientoActivo) {
        public boolean hayAlguna() {
            return pedidosPendientes > 0 || domiciliosActivos > 0 || tarifasPendientes > 0 || emprendimientoActivo;
        }

        /** Solo los conteos distintos de cero, para que la app arme los avisos. */
        public Map<String, Object> detalles() {
            Map<String, Object> detalles = new LinkedHashMap<>();
            if (pedidosPendientes > 0) {
                detalles.put("pedidosPendientes", pedidosPendientes);
            }
            if (domiciliosActivos > 0) {
                detalles.put("domiciliosActivos", domiciliosActivos);
            }
            if (tarifasPendientes > 0) {
                detalles.put("tarifasPendientes", tarifasPendientes);
            }
            if (emprendimientoActivo) {
                detalles.put("emprendimientoActivo", true);
            }
            return detalles;
        }
    }
}
