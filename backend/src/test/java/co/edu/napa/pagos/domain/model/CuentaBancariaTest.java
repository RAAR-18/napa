package co.edu.napa.pagos.domain.model;

import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CuentaBancariaTest {
    @Test
    void trimsBankNameAndRedactsAccountNumberFromToString() {
        CuentaBancaria account = new CuentaBancaria(" Banco ", TipoCuentaBancaria.AHORROS, "00001234");

        assertEquals("Banco", account.banco());
        assertTrue(account.toString().contains("numeroCuenta=***"));
        assertFalse(account.toString().contains("00001234"));
    }

    @Test
    void rejectsBlankOrLongBankName() {
        assertThrows(DomainException.class,
                () -> new CuentaBancaria("  ", TipoCuentaBancaria.AHORROS, "1234"));
        assertThrows(DomainException.class,
                () -> new CuentaBancaria("b".repeat(101), TipoCuentaBancaria.AHORROS, "1234"));
    }

    @Test
    void requiresFourToTwentyDigits() {
        assertThrows(DomainException.class,
                () -> new CuentaBancaria("Banco", TipoCuentaBancaria.AHORROS, "123"));
        assertThrows(DomainException.class,
                () -> new CuentaBancaria("Banco", TipoCuentaBancaria.AHORROS, "1234A"));
        assertThrows(DomainException.class,
                () -> new CuentaBancaria("Banco", TipoCuentaBancaria.AHORROS, "1".repeat(21)));
    }
}
