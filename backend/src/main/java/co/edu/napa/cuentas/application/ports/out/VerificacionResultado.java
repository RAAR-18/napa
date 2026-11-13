package co.edu.napa.cuentas.application.ports.out;

public record VerificacionResultado(
        Estado estado,
        int intentosRestantes
) {
    public VerificacionResultado {
        if (intentosRestantes < 0 || intentosRestantes > 5) {
            throw new IllegalArgumentException("attempts remaining must be between 0 and 5");
        }
    }

    public enum Estado {
        APROBADA,
        INCORRECTA,
        VENCIDA,
        AGOTADA
    }
}
