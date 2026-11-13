package co.edu.napa.cuentas.application.ports.out;

public record IssuedTokens(String accessToken, String refreshToken, long expiresIn) {
}
