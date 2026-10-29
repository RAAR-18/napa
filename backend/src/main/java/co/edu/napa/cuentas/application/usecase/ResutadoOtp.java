package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.out.VerificacionResultado;
import co.edu.napa.shared.domain.DomainException;

import java.util.Map;

/** Traduce el resultado de verificar un código a los errores OTP_* del contrato (RF-70). */
final class ResultadoOtp {
    private ResultadoOtp() {
    }

    static void exigirAprobado(VerificacionResultado resultado) {
        int restantes = resultado.intentosRestantes();
        switch (resultado.estado()) {
            case APROBADA -> {
            }
            case INCORRECTA -> throw new DomainException(
                    "OTP_INVALID",
                    "Código incorrecto. Te quedan " + restantes + " intentos.",
                    Map.of("intentosRestantes", restantes)
            );
            case VENCIDA -> throw new DomainException("OTP_EXPIRED", "El código venció. Pide uno nuevo.");
            case AGOTADA -> throw new DomainException(
                    "OTP_ATTEMPTS_EXHAUSTED",
                    "Se agotaron los intentos. Pide un código nuevo."
            );
        }
    }
}