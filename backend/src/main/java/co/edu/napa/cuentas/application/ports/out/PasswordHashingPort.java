package co.edu.napa.cuentas.application.ports.out;

public interface PasswordHashingPort {
    String hash(String password);

    boolean matches(String password, String passwordHash);
}
