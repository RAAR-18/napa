package co.edu.napa.tarifas.infrastructure.adapters.out.persistence;

import co.edu.napa.tarifas.application.ports.out.TarifaMinimaRepositoryPort;
import co.edu.napa.tarifas.domain.model.TarifaMinima;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TarifaMinimaPersistenceAdapter implements TarifaMinimaRepositoryPort {
    private static final String LOCK_SQL = "SELECT pg_advisory_xact_lock(721044)";
    private static final RowMapper<TarifaMinima> MAPPER = (row, index) -> new TarifaMinima(
            row.getLong("valor"),
            row.getTimestamp("created_at").toInstant()
    );

    private final JdbcTemplate jdbc;

    public TarifaMinimaPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<TarifaMinima> vigente() {
        return jdbc.query("""
                SELECT valor, created_at
                FROM tarifa_minima
                ORDER BY id DESC
                LIMIT 1
                """, MAPPER).stream().findFirst();
    }

    @Override
    public TarifaMinima registrarSiCambio(long valor, UUID administradorId, long valorDefecto) {
        jdbc.execute(LOCK_SQL);
        Optional<TarifaMinima> current = vigente();
        long currentValue = current.map(TarifaMinima::valor).orElse(valorDefecto);
        if (currentValue == valor) {
            return current.orElseGet(() -> new TarifaMinima(valorDefecto, null));
        }

        return jdbc.query("""
                INSERT INTO tarifa_minima (valor, created_at, administrador_id)
                VALUES (?, clock_timestamp(), ?)
                RETURNING valor, created_at
                """, MAPPER, valor, administradorId).getFirst();
    }
}
