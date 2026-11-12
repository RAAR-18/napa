package co.edu.napa.pagos.infrastructure.adapters.out.persistence;

import co.edu.napa.pagos.application.ports.out.ConsultarTipoVendedorPort;
import co.edu.napa.pagos.domain.model.TipoVendedor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ConsultarTipoVendedorPersistenceAdapter implements ConsultarTipoVendedorPort {
    private final JdbcTemplate jdbc;

    public ConsultarTipoVendedorPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<TipoVendedor> consultar(UUID vendedorId) {
        List<String> roles = jdbc.query(
                "SELECT rol FROM usuario WHERE id = ? AND estado = 'ACTIVA'",
                (resultSet, rowNumber) -> resultSet.getString("rol"),
                vendedorId
        );
        return roles.stream().findFirst().flatMap(ConsultarTipoVendedorPersistenceAdapter::mapear);
    }

    private static Optional<TipoVendedor> mapear(String rol) {
        return switch (rol) {
            case "VENDEDOR_AMBULANTE" -> Optional.of(TipoVendedor.AMBULANTE);
            case "VENDEDOR_PUNTO_FIJO" -> Optional.of(TipoVendedor.PUNTO_FIJO);
            default -> Optional.empty();
        };
    }
}
