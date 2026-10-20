package co.edu.napa.cuentas.application.ports.out;

public interface LoginAttemptPort {
    void ensureAllowed(String identifier, String ipAddress);

    void recordFailure(String identifier, String ipAddress);

    void clearIdentifier(String identifier);
}
