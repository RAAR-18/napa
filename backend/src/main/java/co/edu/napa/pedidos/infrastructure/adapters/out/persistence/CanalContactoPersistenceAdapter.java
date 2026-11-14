package co.edu.napa.pedidos.infrastructure.adapters.out.persistence;

import co.edu.napa.pedidos.application.ports.out.CanalContactoRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CanalContactoPersistenceAdapter implements CanalContactoRepositoryPort {
    private final JdbcTemplate jdbc;

    public CanalContactoPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> vendedorDelPedido(UUID pedidoId) {
        return jdbc.queryForList("""
                        SELECT e.usuario_id FROM pedido p JOIN emprendimiento e ON e.id = p.emprendimiento_id
                        WHERE p.id = ?
                        """, UUID.class, pedidoId)
                .stream().findFirst();
    }

    @Override
    public void registrar(UUID id, UUID pedidoId, UUID clienteId, UUID destinatarioId, String destinatario,
                          String tipo, Instant ahora) {
        jdbc.update("""
                INSERT INTO canal_contacto (id, pedido_id, cliente_id, destinatario_id, destinatario, tipo, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, pedidoId, clienteId, destinatarioId, destinatario, tipo, Timestamp.from(ahora));
    }
}
