package co.edu.napa.cuentas.application.ports.out;

public interface VerificationRateLimitPort {
    void checkAndRecord(String celular, String ipAddress);
}
