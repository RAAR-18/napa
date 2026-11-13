package co.edu.napa.pedidos.application.ports.out;

import java.math.BigDecimal;

public interface TarifaDomicilioConfiguracionPort {
    long base();
    long valorPorKm();
    long valorPorMin();
    BigDecimal factorLineaRecta();
}
