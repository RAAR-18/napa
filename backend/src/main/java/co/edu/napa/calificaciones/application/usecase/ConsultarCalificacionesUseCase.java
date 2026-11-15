package co.edu.napa.calificaciones.application.usecase;

import co.edu.napa.calificaciones.application.ports.in.ConsultarCalificacionesPort;
import co.edu.napa.calificaciones.application.ports.out.CalificacionRepositoryPort;
import co.edu.napa.calificaciones.application.ports.out.CalificacionRepositoryPort.CalificacionListada;
import co.edu.napa.calificaciones.application.ports.out.CalificacionRepositoryPort.Filtro;
import co.edu.napa.calificaciones.application.ports.out.CalificacionRepositoryPort.Orden;
import co.edu.napa.calificaciones.application.ports.out.InteraccionesPort;
import co.edu.napa.calificaciones.domain.model.EstadoCalificacion;
import co.edu.napa.calificaciones.domain.model.RolUsuario;
import co.edu.napa.calificaciones.domain.model.TipoEntidad;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class ConsultarCalificacionesUseCase implements ConsultarCalificacionesPort {
    private static final String NO_ENCONTRADA = "No encontramos esa entidad.";
    private static final String SIN_PERMISO = "No tienes permiso para ver estas calificaciones.";
    static final int MAXIMO_RESUMENES = 50;

    private final CalificacionRepositoryPort calificaciones;
    private final InteraccionesPort interacciones;

    public ConsultarCalificacionesUseCase(CalificacionRepositoryPort calificaciones, InteraccionesPort interacciones) {
        this.calificaciones = Objects.requireNonNull(calificaciones, "calificaciones");
        this.interacciones = Objects.requireNonNull(interacciones, "interacciones");
    }

    /** RF-02: solo las ACTIVA de un destino concreto, según la tabla de autorización del plan. */
    @Override
    @Transactional(readOnly = true)
    public Pagina<CalificacionPublica> consultar(UUID usuarioId, RolUsuario rol, Consulta consulta) {
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(consulta, "consulta");
        if (consulta.tipoEntidad() == null || consulta.entidadId() == null) {
            throw new DomainException("VALIDATION_ERROR", "Indica la entidad que quieres consultar.",
                    Map.of("fields", Map.of("tipoEntidad", "Obligatorio", "entidadId", "Obligatorio")));
        }
        autorizar(usuarioId, rol, consulta.tipoEntidad(), consulta.entidadId());
        Filtro filtro = new Filtro(consulta.tipoEntidad(), consulta.entidadId(), EstadoCalificacion.ACTIVA);
        List<CalificacionPublica> content = calificaciones.listar(filtro, consulta.page(), consulta.size(), orden(consulta))
                .stream()
                .map(c -> new CalificacionPublica(c.id(), c.puntaje(), c.comentario(), c.autorNombre(),
                        usuarioId.equals(c.autorId()), c.createdAt()))
                .toList();
        return new Pagina<>(content, consulta.page(), consulta.size(), calificaciones.contar(filtro),
                resumen(consulta.tipoEntidad(), consulta.entidadId()));
    }

    /** RF-03 y RF-06: el administrador ve todas, filtra por estado (ACTIVA por defecto) y por entidad. */
    @Override
    @Transactional(readOnly = true)
    public Pagina<CalificacionSupervisada> supervisar(Consulta consulta) {
        Objects.requireNonNull(consulta, "consulta");
        if (consulta.entidadId() != null && consulta.tipoEntidad() == null) {
            throw new DomainException("VALIDATION_ERROR", "entidadId requiere tipoEntidad.",
                    Map.of("fields", Map.of("tipoEntidad", "Obligatorio con entidadId")));
        }
        boolean porEntidad = consulta.tipoEntidad() != null && consulta.entidadId() != null;
        if (porEntidad) {
            existeEntidad(consulta.tipoEntidad(), consulta.entidadId());
        }
        EstadoCalificacion estado = Optional.ofNullable(consulta.estado()).orElse(EstadoCalificacion.ACTIVA);
        Filtro filtro = new Filtro(consulta.tipoEntidad(), consulta.entidadId(), estado);
        List<CalificacionSupervisada> content = calificaciones.listar(filtro, consulta.page(), consulta.size(),
                        orden(consulta))
                .stream()
                .map(ConsultarCalificacionesUseCase::supervisada)
                .toList();
        return new Pagina<>(content, consulta.page(), consulta.size(), calificaciones.contar(filtro),
                porEntidad ? resumen(consulta.tipoEntidad(), consulta.entidadId()) : null);
    }

    @Override
    @Transactional(readOnly = true)
    public Resumen resumen(TipoEntidad tipoEntidad, UUID entidadId) {
        return redondear(calificaciones.resumen(tipoEntidad, entidadId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResumenEntidad> resumenes(UUID usuarioId, RolUsuario rol, TipoEntidad tipo, List<UUID> entidadIds) {
        Objects.requireNonNull(usuarioId, "usuarioId");
        if (tipo == null || tipo == TipoEntidad.USUARIO) {
            throw new DomainException("VALIDATION_ERROR", "Solo hay resúmenes de emprendimientos o productos.",
                    Map.of("fields", Map.of("tipoEntidad", "Usa EMPRENDIMIENTO o PRODUCTO")));
        }
        if (entidadIds.isEmpty() || entidadIds.size() > MAXIMO_RESUMENES) {
            throw new DomainException("VALIDATION_ERROR", "Pide entre 1 y 50 identificadores.",
                    Map.of("fields", Map.of("ids", "Entre 1 y 50")));
        }
        List<UUID> unicos = entidadIds.stream().distinct().toList();
        Map<UUID, UUID> duenios = interacciones.duenios(tipo, unicos);
        List<UUID> visibles = unicos.stream()
                .filter(duenios::containsKey)
                .filter(id -> !rol.esVendedor() || usuarioId.equals(duenios.get(id)))
                .toList();
        if (visibles.isEmpty()) {
            return List.of();
        }
        Map<UUID, CalificacionRepositoryPort.Resumen> encontrados = calificaciones.resumenes(tipo, visibles);
        return visibles.stream().map(id -> {
            Resumen r = redondear(encontrados.get(id));
            return new ResumenEntidad(id, r.promedio(), r.total());
        }).toList();
    }

    private static Resumen redondear(CalificacionRepositoryPort.Resumen resumen) {
        if (resumen == null || resumen.total() == 0 || resumen.promedio() == null) {
            return new Resumen(null, 0);
        }
        return new Resumen(BigDecimal.valueOf(resumen.promedio()).setScale(1, RoundingMode.HALF_UP).doubleValue(),
                resumen.total());
    }

    private void autorizar(UUID usuarioId, RolUsuario rol, TipoEntidad tipo, UUID entidadId) {
        switch (tipo) {
            case EMPRENDIMIENTO -> {
                UUID duenio = interacciones.duenioEmprendimiento(entidadId).orElseThrow(this::noEncontrada);
                if (rol.esVendedor() && !usuarioId.equals(duenio)) {
                    throw sinPermiso();
                }
            }
            case PRODUCTO -> {
                UUID duenio = interacciones.duenioProducto(entidadId).orElseThrow(this::noEncontrada);
                if (rol.esVendedor() && !usuarioId.equals(duenio)) {
                    throw sinPermiso();
                }
            }
            case USUARIO -> {
                String rolDestino = interacciones.rolUsuario(entidadId).orElseThrow(this::noEncontrada);
                if ("CLIENTE".equals(rolDestino)) {
                    if (rol.esVendedor() && !interacciones.clienteComproAVendedor(usuarioId, entidadId)) {
                        throw sinPermiso();
                    }
                } else if ("DOMICILIARIO".equals(rolDestino)) {
                    if (rol == RolUsuario.VENDEDOR_AMBULANTE
                            || (rol == RolUsuario.VENDEDOR_PUNTO_FIJO
                            && !interacciones.domiciliarioLlevoPedidoDeVendedor(usuarioId, entidadId))) {
                        throw sinPermiso();
                    }
                } else {
                    throw noEncontrada();
                }
            }
        }
    }

    private void existeEntidad(TipoEntidad tipo, UUID entidadId) {
        boolean existe = switch (tipo) {
            case EMPRENDIMIENTO -> interacciones.duenioEmprendimiento(entidadId).isPresent();
            case PRODUCTO -> interacciones.duenioProducto(entidadId).isPresent();
            case USUARIO -> interacciones.rolUsuario(entidadId).isPresent();
        };
        if (!existe) {
            throw noEncontrada();
        }
    }

    private static Orden orden(Consulta consulta) {
        String campo = consulta.sortCampo() == null ? "createdAt" : consulta.sortCampo();
        return new Orden(campo, consulta.sortCampo() != null && consulta.ascendente());
    }

    private static CalificacionSupervisada supervisada(CalificacionListada c) {
        return new CalificacionSupervisada(c.id(), c.puntaje(), c.comentario(), c.estado().name(), c.moderada(),
                new Autor(c.autorId(), c.autorNombre(), c.autorRol()),
                new Entidad(c.tipoEntidad().name(), c.entidadId(), c.entidadNombre()),
                c.pedidoId(), c.createdAt(), c.updatedAt());
    }

    private DomainException noEncontrada() {
        return new DomainException("NOT_FOUND", NO_ENCONTRADA);
    }

    private static DomainException sinPermiso() {
        return new DomainException("FORBIDDEN", SIN_PERMISO);
    }
}
