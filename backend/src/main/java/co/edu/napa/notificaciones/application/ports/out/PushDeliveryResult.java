package co.edu.napa.notificaciones.application.ports.out;

public record PushDeliveryResult(
        int targetCount,
        int succeededCount,
        int failedCount,
        boolean providerAvailable
) {
    public PushDeliveryResult {
        if (targetCount < 0 || succeededCount < 0 || failedCount < 0
                || succeededCount + failedCount != targetCount) {
            throw new IllegalArgumentException("Push delivery counts must be non-negative and add up to targets");
        }
    }
}
