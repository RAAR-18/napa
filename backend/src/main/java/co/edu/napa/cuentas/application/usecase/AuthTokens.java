package co.edu.napa.cuentas.application.usecase;

public record AuthTokens(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenType,
        String rol
) {
}
