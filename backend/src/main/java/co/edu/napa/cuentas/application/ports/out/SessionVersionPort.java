package co.edu.napa.cuentas.application.ports.out;

import java.time.Duration;
import java.util.UUID;

public interface SessionVersionPort {
    void set(UUID sessionId, long version, Duration ttl);

    boolean matches(UUID sessionId, long version);

    void revoke(UUID sessionId, long nextVersion, Duration ttl);
}
