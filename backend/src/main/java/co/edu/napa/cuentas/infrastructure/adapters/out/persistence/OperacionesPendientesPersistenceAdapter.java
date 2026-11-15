package co.edu.napa.cuentas.infrastructure.adapters.out.persistence;

import co.edu.napa.cuentas.application.ports.out.OperacionesPendientesPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Solo lectura de pedidos, domicilios, pagos y emprendimientos. */
@Repository
public class OperacionesPendientesPersistenceAdapter implements OperacionesPendientesPort {
    private final JdbcTemplate jdbc;

    public OperacionesPendientesPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public OperacionesPendientes contar(UUID usuarioId) {
        return jdbc.queryForObject("""
                SELECT
                    (SELECT COUNT(*) FROM pedido p JOIN emprendimiento e ON e.id = p.emprendimiento_id
                      WHERE (p.usuario_id = ? OR e.usuario_id = ?)
                        AND p.estado IN ('PENDIENTE', 'ACEPTADO', 'LISTO_PARA_RECOGER', 'EN_CAMINO')) AS pedidos,
                    (SELECT COUNT(*) FROM domicilio d
                      WHERE ? IN (d.comprador_id, d.vendedor_id, d.domiciliario_id)
                        AND d.estado IN ('DISPONIBLE', 'ASIGNADO', 'RECOGIDO', 'EN_CAMINO')) AS domicilios,
                    (SELECT COUNT(*) FROM domicilio d
                      WHERE (d.vendedor_id = ? OR d.domiciliario_id = ?) AND d.estado = 'ENTREGADO'
                        AND NOT EXISTS (SELECT 1 FROM pago pg WHERE pg.domicilio_id = d.id
                                          AND pg.tipo = 'TARIFA_DOMICILIO' AND pg.estado = 'CONFIRMADO')) AS tarifas,
                    EXISTS (SELECT 1 FROM emprendimiento WHERE usuario_id = ? AND estado = 'ACTIVO') AS emprendimiento
                """,
                (rs, i) -> new OperacionesPendientes(rs.getLong("pedidos"), rs.getLong("domicilios"),
                        rs.getLong("tarifas"), rs.getBoolean("emprendimiento")),
                usuarioId, usuarioId, usuarioId, usuarioId, usuarioId, usuarioId);
    }
}
