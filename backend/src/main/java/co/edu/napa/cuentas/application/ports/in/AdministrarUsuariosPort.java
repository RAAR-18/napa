package co.edu.napa.cuentas.application.ports.in;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** RF-66 (consultar), RF-67 (editar), RF-68 (eliminar) y RF-69 (listar) usuarios como administrador. */
public interface AdministrarUsuariosPort {
    Pagina listar(Filtro filtro, int page, int size, String sortCampo, boolean ascendente);

    PerfilUsuario consultar(UUID usuarioId);

    PerfilUsuario editar(UUID administradorId, UUID usuarioId, Cambios cambios);

    void eliminar(UUID administradorId, UUID usuarioId, boolean forzar);

    /** {@code roles} vacío = todos; {@code estado} y {@code q} nulos = sin filtro. */
    record Filtro(Set<String> roles, String estado, String q) {}

    /** Campos nulos no cambian. */
    record Cambios(String nombre, String celular, String correo, String rol, PerfilUsuario.Ubicacion ubicacion) {
        public boolean vacio() {
            return nombre == null && celular == null && correo == null && rol == null && ubicacion == null;
        }
    }

    record Pagina(List<UsuarioResumen> content, int page, int size, long totalElements) {
        public Pagina {
            content = List.copyOf(content);
        }
    }

    record UsuarioResumen(UUID id, String nombre, String rol, String estado, Instant createdAt) {}
}
