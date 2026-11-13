package co.edu.napa.pagos.infrastructure.adapters.out.persistence;

import co.edu.napa.pagos.application.ports.out.ConfiguracionPagoRepositoryPort;
import co.edu.napa.pagos.domain.model.ConfiguracionPago;
import co.edu.napa.pagos.domain.model.CuentaBancaria;
import co.edu.napa.pagos.domain.model.TipoCuentaBancaria;
import co.edu.napa.pagos.infrastructure.security.CuentaBancariaCipher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ConfiguracionPagoPersistenceAdapter implements ConfiguracionPagoRepositoryPort {
    private static final String SELECT = """
            SELECT usuario_id, acepta_efectivo, banco, numero_cuenta_cifrada, tipo_cuenta, created_at, updated_at
            FROM configuracion_pago
            WHERE usuario_id = ?
            """;

    private final JdbcTemplate jdbc;
    private final CuentaBancariaCipher cipher;

    public ConfiguracionPagoPersistenceAdapter(JdbcTemplate jdbc, CuentaBancariaCipher cipher) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.cipher = Objects.requireNonNull(cipher, "cipher");
    }

    @Override
    public ConfiguracionPago save(ConfiguracionPago configuracion) {
        CuentaBancaria cuenta = configuracion.cuentaBancaria();
        return jdbc.queryForObject("""
                INSERT INTO configuracion_pago (
                    id, usuario_id, acepta_efectivo, banco, numero_cuenta_cifrada, tipo_cuenta,
                    created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, clock_timestamp(), clock_timestamp())
                ON CONFLICT (usuario_id) DO UPDATE SET
                    acepta_efectivo = EXCLUDED.acepta_efectivo,
                    banco = EXCLUDED.banco,
                    numero_cuenta_cifrada = EXCLUDED.numero_cuenta_cifrada,
                    tipo_cuenta = EXCLUDED.tipo_cuenta,
                    updated_at = clock_timestamp()
                RETURNING usuario_id, acepta_efectivo, banco, numero_cuenta_cifrada, tipo_cuenta,
                          created_at, updated_at
                """, rowMapper(), UUID.randomUUID(), configuracion.vendedorId(),
                configuracion.aceptaEfectivo(),
                cuenta == null ? null : cuenta.banco(),
                cuenta == null ? null : cipher.encrypt(cuenta.numeroCuenta(), configuracion.vendedorId()),
                cuenta == null ? null : cuenta.tipoCuenta().name());
    }

    @Override
    public Optional<ConfiguracionPago> findByVendedorId(UUID vendedorId) {
        List<ConfiguracionPago> found = jdbc.query(SELECT, rowMapper(), vendedorId);
        return found.stream().findFirst();
    }

    private RowMapper<ConfiguracionPago> rowMapper() {
        return (resultSet, rowNumber) -> toDomain(resultSet);
    }

    private ConfiguracionPago toDomain(ResultSet resultSet) throws SQLException {
        UUID vendedorId = resultSet.getObject("usuario_id", UUID.class);
        String banco = resultSet.getString("banco");
        CuentaBancaria cuenta = banco == null ? null : new CuentaBancaria(
                banco,
                TipoCuentaBancaria.valueOf(resultSet.getString("tipo_cuenta")),
                cipher.decrypt(resultSet.getString("numero_cuenta_cifrada"), vendedorId)
        );
        return new ConfiguracionPago(
                vendedorId,
                resultSet.getBoolean("acepta_efectivo"),
                cuenta,
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }
}
