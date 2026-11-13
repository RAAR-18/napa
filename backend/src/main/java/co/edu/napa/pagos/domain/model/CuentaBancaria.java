package co.edu.napa.pagos.domain.model;

import co.edu.napa.shared.domain.DomainException;

import java.util.Objects;

public record CuentaBancaria(String banco, TipoCuentaBancaria tipoCuenta, String numeroCuenta) {
    public CuentaBancaria {
        Objects.requireNonNull(banco, "banco");
        Objects.requireNonNull(tipoCuenta, "tipoCuenta");
        Objects.requireNonNull(numeroCuenta, "numeroCuenta");
        banco = banco.trim();
        if (banco.isEmpty() || banco.length() > 100) {
            throw new DomainException("VALIDATION_ERROR", "El banco debe tener entre 1 y 100 caracteres.");
        }
        if (!numeroCuenta.matches("\\d{4,20}")) {
            throw new DomainException("VALIDATION_ERROR", "La cuenta debe contener entre 4 y 20 dígitos.");
        }
    }

    @Override
    public String toString() {
        return "CuentaBancaria[banco=" + banco + ", tipoCuenta=" + tipoCuenta + ", numeroCuenta=***]";
    }
}
