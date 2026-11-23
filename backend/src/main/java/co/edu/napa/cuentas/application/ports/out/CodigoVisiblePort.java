package co.edu.napa.cuentas.application.ports.out;

import java.util.Optional;

/**
 * Proveedor que permite mostrar el código en pantalla, mientras no haya SMS real.
 * Solo lo implementa el proveedor "imagen", y solo para el registro.
 */
public interface CodigoVisiblePort {
    /** Código vigente del envío con ese identificador de proveedor; vacío si venció o fue reemplazado. */
    Optional<String> codigoVigente(String proveedorId);
}
