package co.edu.napa.domicilios.infrastructure.adapters.out.persistence;

import co.edu.napa.domicilios.application.ports.in.ListarDomiciliosVendedorPort.Filtro;
import co.edu.napa.domicilios.application.ports.in.SupervisarDomiciliosPort;
import co.edu.napa.domicilios.application.ports.out.DomicilioDisponibleProjection;
import co.edu.napa.domicilios.application.ports.out.DomicilioSupervisionProjection;
import co.edu.napa.domicilios.application.ports.out.DomicilioRepositoryPort;
import co.edu.napa.domicilios.domain.model.Domicilio;
import co.edu.napa.domicilios.domain.model.EstadoDomicilio;
import co.edu.napa.domicilios.domain.model.EventoTrazabilidad;
import co.edu.napa.domicilios.domain.model.MotivoCancelacion;
import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.domain.geo.GeoPoint;
import co.edu.napa.shared.domain.geo.Ubicacion;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DomicilioPersistenceAdapter implements DomicilioRepositoryPort {
    private static final String COLUMNAS = """
            id, pedido_id, vendedor_id, comprador_id, domiciliario_id, estado, tarifa, ubicacion_origen_id,
            ubicacion_destino_id, codigo_confirmacion, asignado_at, motivo_cancelacion, detalle_cancelacion,
            aviso_sin_asignar_at, created_at, updated_at
            """;

    private static final RowMapper<Domicilio> MAPPER = (row, index) -> new Domicilio(
            row.getObject("id", UUID.class),
            row.getObject("pedido_id", UUID.class),
            row.getObject("vendedor_id", UUID.class),
            row.getObject("comprador_id", UUID.class),
            row.getObject("domiciliario_id", UUID.class),
            EstadoDomicilio.valueOf(row.getString("estado")),
            row.getLong("tarifa"),
            row.getObject("ubicacion_origen_id", UUID.class),
            row.getObject("ubicacion_destino_id", UUID.class),
            row.getString("codigo_confirmacion"),
            instant(row.getTimestamp("asignado_at")),
            row.getString("motivo_cancelacion") == null ? null
                    : MotivoCancelacion.valueOf(row.getString("motivo_cancelacion")),
            row.getString("detalle_cancelacion"),
            instant(row.getTimestamp("aviso_sin_asignar_at")),
            row.getTimestamp("created_at").toInstant(),
            row.getTimestamp("updated_at").toInstant()
    );

    /** Domicilio con sus puntos A y B (RF-12); las columnas del domicilio conservan su nombre para {@link #MAPPER}. */
    private static final String SUPERVISION_DESDE = """
            SELECT d.id, d.pedido_id, d.vendedor_id, d.comprador_id, d.domiciliario_id, d.estado, d.tarifa,
                d.ubicacion_origen_id, d.ubicacion_destino_id, d.codigo_confirmacion, d.asignado_at,
                d.motivo_cancelacion, d.detalle_cancelacion, d.aviso_sin_asignar_at, d.created_at, d.updated_at,
                o.id AS o_id, o.latitud AS o_latitud, o.longitud AS o_longitud, o.direccion AS o_direccion,
                o.referencia AS o_referencia, o.barrio AS o_barrio, o.created_at AS o_created_at,
                o.updated_at AS o_updated_at,
                b.id AS b_id, b.latitud AS b_latitud, b.longitud AS b_longitud, b.direccion AS b_direccion,
                b.referencia AS b_referencia, b.barrio AS b_barrio, b.created_at AS b_created_at,
                b.updated_at AS b_updated_at
            FROM domicilio d
            JOIN ubicacion o ON o.id = d.ubicacion_origen_id
            JOIN ubicacion b ON b.id = d.ubicacion_destino_id
            """;

    private static final RowMapper<DomicilioSupervisionProjection> SUPERVISION = (row, index) ->
            new DomicilioSupervisionProjection(MAPPER.mapRow(row, index), ubicacion(row, "o_"), ubicacion(row, "b_"));

    private final JdbcTemplate jdbc;

    public DomicilioPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public void guardar(Domicilio domicilio) {
        try {
            jdbc.update("INSERT INTO domicilio (" + COLUMNAS
                            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    domicilio.id(), domicilio.pedidoId(), domicilio.vendedorId(), domicilio.compradorId(),
                    domicilio.domiciliarioId(), domicilio.estado().name(), domicilio.tarifa(),
                    domicilio.ubicacionOrigenId(), domicilio.ubicacionDestinoId(), domicilio.codigoConfirmacion(),
                    timestamp(domicilio.asignadoAt()),
                    domicilio.motivoCancelacion() == null ? null : domicilio.motivoCancelacion().name(),
                    domicilio.detalleCancelacion(),
                    timestamp(domicilio.avisoSinAsignarAt()), Timestamp.from(domicilio.createdAt()),
                    Timestamp.from(domicilio.updatedAt()));
        } catch (DuplicateKeyException exception) {
            throw new DomainException("ALREADY_PUBLISHED", "Este pedido ya tiene un domicilio activo.");
        }
    }

    @Override
    public void actualizar(Domicilio domicilio) {
        int updated = jdbc.update("""
                UPDATE domicilio
                SET estado = ?, domiciliario_id = ?, codigo_confirmacion = ?, asignado_at = ?,
                    motivo_cancelacion = ?, detalle_cancelacion = ?, aviso_sin_asignar_at = ?, updated_at = ?
                WHERE id = ?
                """,
                domicilio.estado().name(), domicilio.domiciliarioId(), domicilio.codigoConfirmacion(),
                timestamp(domicilio.asignadoAt()),
                domicilio.motivoCancelacion() == null ? null : domicilio.motivoCancelacion().name(),
                domicilio.detalleCancelacion(),
                timestamp(domicilio.avisoSinAsignarAt()), Timestamp.from(domicilio.updatedAt()), domicilio.id());
        if (updated != 1) {
            throw new DomainException("CONFLICT", "El domicilio cambió durante la operación.");
        }
    }

    @Override
    public void registrarEvento(EventoTrazabilidad evento) {
        jdbc.update("""
                INSERT INTO evento_trazabilidad (id, domicilio_id, estado, usuario_id, ocurrido_at)
                VALUES (?, ?, ?, ?, ?)
                """, evento.id(), evento.domicilioId(), evento.estado().name(), evento.usuarioId(),
                Timestamp.from(evento.ocurridoAt()));
    }

    @Override
    public List<EventoTrazabilidad> listarEventos(UUID domicilioId) {
        return jdbc.query("""
                SELECT id, domicilio_id, estado, usuario_id, ocurrido_at
                FROM evento_trazabilidad
                WHERE domicilio_id = ?
                ORDER BY ocurrido_at, id
                """, (row, index) -> new EventoTrazabilidad(
                row.getObject("id", UUID.class),
                row.getObject("domicilio_id", UUID.class),
                EstadoDomicilio.valueOf(row.getString("estado")),
                row.getObject("usuario_id", UUID.class),
                row.getTimestamp("ocurrido_at").toInstant()
        ), domicilioId);
    }

    @Override
    public Optional<Domicilio> buscar(UUID domicilioId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM domicilio WHERE id = ?", MAPPER, domicilioId)
                .stream().findFirst();
    }

    @Override
    public Optional<Domicilio> bloquear(UUID domicilioId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM domicilio WHERE id = ? FOR UPDATE", MAPPER, domicilioId)
                .stream().findFirst();
    }

    @Override
    public Optional<Domicilio> buscarActivoPorPedido(UUID pedidoId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM domicilio WHERE pedido_id = ? AND estado <> 'CANCELADO'",
                MAPPER, pedidoId).stream().findFirst();
    }

    @Override
    public Optional<Domicilio> buscarUltimoPorPedido(UUID pedidoId) {
        return jdbc.query("SELECT " + COLUMNAS + """
                FROM domicilio WHERE pedido_id = ?
                ORDER BY (estado <> 'CANCELADO') DESC, created_at DESC, id DESC
                LIMIT 1
                """, MAPPER, pedidoId).stream().findFirst();
    }

    @Override
    public List<Domicilio> listarVendedor(UUID vendedorId, Filtro filtro, long offset, int limit) {
        List<Object> args = new ArrayList<>();
        String where = whereVendedor(vendedorId, filtro, args);
        String orden = filtro.ascendente() ? "ASC" : "DESC";
        args.add(limit);
        args.add(offset);
        return jdbc.query("SELECT " + COLUMNAS + " FROM domicilio " + where
                + " ORDER BY created_at " + orden + ", id " + orden + " LIMIT ? OFFSET ?", MAPPER, args.toArray());
    }

    @Override
    public long contarVendedor(UUID vendedorId, Filtro filtro) {
        List<Object> args = new ArrayList<>();
        String where = whereVendedor(vendedorId, filtro, args);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM domicilio " + where, Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    @Override
    public List<DomicilioSupervisionProjection> listarGeneral(
            SupervisarDomiciliosPort.Filtro filtro, long offset, int limit) {
        List<Object> args = new ArrayList<>();
        String where = whereGeneral(filtro, args);
        String columna = filtro.porActualizacion() ? "d.updated_at" : "d.created_at";
        String orden = filtro.ascendente() ? "ASC" : "DESC";
        args.add(limit);
        args.add(offset);
        return jdbc.query(SUPERVISION_DESDE + where + " ORDER BY " + columna + " " + orden + ", d.id " + orden
                + " LIMIT ? OFFSET ?", SUPERVISION, args.toArray());
    }

    @Override
    public long contarGeneral(SupervisarDomiciliosPort.Filtro filtro) {
        List<Object> args = new ArrayList<>();
        String where = whereGeneral(filtro, args);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM domicilio d" + where, Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    @Override
    public Optional<DomicilioSupervisionProjection> buscarConPuntos(UUID domicilioId) {
        return jdbc.query(SUPERVISION_DESDE + " WHERE d.id = ?", SUPERVISION, domicilioId).stream().findFirst();
    }

    @Override
    public List<Domicilio> bloquearSinAvisoPublicadosAntesDe(Instant limite, int maximo) {
        return jdbc.query("SELECT " + COLUMNAS + """
                FROM domicilio
                WHERE estado = 'DISPONIBLE' AND aviso_sin_asignar_at IS NULL AND created_at <= ?
                ORDER BY created_at
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """, MAPPER, Timestamp.from(limite), maximo);
    }

    @Override
    public List<Domicilio> bloquearDisponiblesPublicadosAntesDe(Instant limite, int maximo) {
        return jdbc.query("SELECT " + COLUMNAS + """
                FROM domicilio
                WHERE estado = 'DISPONIBLE' AND created_at <= ?
                ORDER BY created_at
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """, MAPPER, Timestamp.from(limite), maximo);
    }

    @Override
    public void bloquearDomiciliario(UUID domiciliarioId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                (row, index) -> null, "domiciliario:" + domiciliarioId);
    }

    @Override
    public List<Domicilio> listarEnCurso(UUID domiciliarioId) {
        return jdbc.query("SELECT " + COLUMNAS + """
                FROM domicilio
                WHERE domiciliario_id = ? AND estado IN ('ASIGNADO', 'RECOGIDO', 'EN_CAMINO')
                ORDER BY asignado_at, id
                """, MAPPER, domiciliarioId);
    }

    @Override
    public void registrarRechazo(UUID domiciliarioId, UUID domicilioId, Instant now) {
        jdbc.update("""
                INSERT INTO domicilio_rechazo (domiciliario_id, domicilio_id, created_at)
                VALUES (?, ?, ?)
                ON CONFLICT (domiciliario_id, domicilio_id) DO NOTHING
                """, domiciliarioId, domicilioId, Timestamp.from(now));
    }

    @Override
    public boolean fueRechazadoPor(UUID domiciliarioId, UUID domicilioId) {
        Boolean existe = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM domicilio_rechazo WHERE domiciliario_id = ? AND domicilio_id = ?)
                """, Boolean.class, domiciliarioId, domicilioId);
        return Boolean.TRUE.equals(existe);
    }

    @Override
    public List<DomicilioDisponibleProjection> listarDisponibles(
            UUID domiciliarioId, OrdenDisponibles orden, GeoPoint posicion, long radioMetros, long offset,
            int limit) {
        List<Object> args = new ArrayList<>();
        args.add(posicion.longitude());
        args.add(posicion.latitude());
        args.add(domiciliarioId);
        args.add(posicion.longitude());
        args.add(posicion.latitude());
        args.add(radioMetros);
        String orderBy = switch (orden) {
            case CERCANIA -> "distancia_al_origen ASC NULLS LAST, d.created_at DESC, d.id";
            case GANANCIA -> "d.tarifa DESC, d.created_at DESC, d.id";
            case RECIENTES -> "d.created_at DESC, d.id";
        };
        args.add(limit);
        args.add(offset);
        return jdbc.query("""
                SELECT d.id, d.tarifa, d.created_at,
                    o.id AS o_id, o.latitud AS o_latitud, o.longitud AS o_longitud, o.direccion AS o_direccion,
                    o.referencia AS o_referencia, o.barrio AS o_barrio, o.created_at AS o_created_at,
                    o.updated_at AS o_updated_at,
                    b.id AS b_id, b.latitud AS b_latitud, b.longitud AS b_longitud, b.direccion AS b_direccion,
                    b.referencia AS b_referencia, b.barrio AS b_barrio, b.created_at AS b_created_at,
                    b.updated_at AS b_updated_at,
                    ST_Distance(o.punto, b.punto) AS distancia,
                    ST_Distance(o.punto, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography) AS distancia_al_origen
                FROM domicilio d
                JOIN ubicacion o ON o.id = d.ubicacion_origen_id
                JOIN ubicacion b ON b.id = d.ubicacion_destino_id
                WHERE d.estado = 'DISPONIBLE'
                  AND NOT EXISTS (SELECT 1 FROM domicilio_rechazo r
                                  WHERE r.domicilio_id = d.id AND r.domiciliario_id = ?)
                  AND ST_DWithin(o.punto, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)
                ORDER BY %s
                LIMIT ? OFFSET ?
                """.formatted(orderBy), (row, index) -> new DomicilioDisponibleProjection(
                row.getObject("id", UUID.class),
                row.getLong("tarifa"),
                row.getTimestamp("created_at").toInstant(),
                ubicacion(row, "o_"),
                ubicacion(row, "b_"),
                Math.round(row.getDouble("distancia")),
                row.getObject("distancia_al_origen") == null ? null
                        : Math.round(row.getDouble("distancia_al_origen"))
        ), args.toArray());
    }

    @Override
    public long contarDisponibles(UUID domiciliarioId, GeoPoint posicion, long radioMetros) {
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM domicilio d
                JOIN ubicacion o ON o.id = d.ubicacion_origen_id
                WHERE d.estado = 'DISPONIBLE'
                  AND NOT EXISTS (SELECT 1 FROM domicilio_rechazo r
                                  WHERE r.domicilio_id = d.id AND r.domiciliario_id = ?)
                  AND ST_DWithin(o.punto, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)
                """, Long.class, domiciliarioId, posicion.longitude(), posicion.latitude(), radioMetros);
        return total == null ? 0 : total;
    }

    @Override
    public long distanciaMetros(UUID ubicacionOrigenId, UUID ubicacionDestinoId) {
        Double distancia = jdbc.queryForObject("""
                SELECT ST_Distance(o.punto, b.punto) FROM ubicacion o, ubicacion b WHERE o.id = ? AND b.id = ?
                """, Double.class, ubicacionOrigenId, ubicacionDestinoId);
        return distancia == null ? 0 : Math.round(distancia);
    }

    private static Ubicacion ubicacion(ResultSet row, String prefijo) throws SQLException {
        return new Ubicacion(
                row.getObject(prefijo + "id", UUID.class),
                row.getDouble(prefijo + "latitud"),
                row.getDouble(prefijo + "longitud"),
                row.getString(prefijo + "direccion"),
                row.getString(prefijo + "referencia"),
                row.getString(prefijo + "barrio"),
                row.getTimestamp(prefijo + "created_at").toInstant(),
                row.getTimestamp(prefijo + "updated_at").toInstant());
    }

    private static String whereVendedor(UUID vendedorId, Filtro filtro, List<Object> args) {
        StringBuilder where = new StringBuilder("WHERE vendedor_id = ?");
        args.add(vendedorId);
        if (filtro.estado() != null) {
            where.append(" AND estado = ?");
            args.add(filtro.estado().name());
        }
        if (filtro.desde() != null) {
            where.append(" AND created_at >= ?");
            args.add(Timestamp.from(filtro.desde()));
        }
        if (filtro.hasta() != null) {
            where.append(" AND created_at < ?");
            args.add(Timestamp.from(filtro.hasta()));
        }
        return where.toString();
    }

    private static String whereGeneral(SupervisarDomiciliosPort.Filtro filtro, List<Object> args) {
        List<String> condiciones = new ArrayList<>();
        if (filtro.estado() != null) {
            condiciones.add("d.estado = ?");
            args.add(filtro.estado().name());
        }
        if (filtro.desde() != null) {
            condiciones.add("d.created_at >= ?");
            args.add(Timestamp.from(filtro.desde()));
        }
        if (filtro.hasta() != null) {
            condiciones.add("d.created_at < ?");
            args.add(Timestamp.from(filtro.hasta()));
        }
        if (filtro.vendedorId() != null) {
            condiciones.add("d.vendedor_id = ?");
            args.add(filtro.vendedorId());
        }
        if (filtro.domiciliarioId() != null) {
            condiciones.add("d.domiciliario_id = ?");
            args.add(filtro.domiciliarioId());
        }
        return condiciones.isEmpty() ? "" : " WHERE " + String.join(" AND ", condiciones);
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
