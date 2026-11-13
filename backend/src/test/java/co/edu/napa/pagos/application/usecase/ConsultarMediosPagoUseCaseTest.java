package co.edu.napa.pagos.application.usecase;

import co.edu.napa.pagos.application.ports.out.ConfiguracionPagoRepositoryPort;
import co.edu.napa.pagos.application.ports.out.ConsultarTipoVendedorPort;
import co.edu.napa.pagos.domain.model.ConfiguracionPago;
import co.edu.napa.pagos.domain.model.CuentaBancaria;
import co.edu.napa.pagos.domain.model.TipoCuentaBancaria;
import co.edu.napa.pagos.domain.model.TipoVendedor;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsultarMediosPagoUseCaseTest {
    private static final UUID VENDOR_ID = UUID.randomUUID();

    @Test
    void returnsNoMethodsWhenVendorHasNotConfiguredPayments() {
        var result = new ConsultarMediosPagoUseCase(repository(Optional.empty()),
                vendorType(TipoVendedor.PUNTO_FIJO)).consultar(VENDOR_ID);

        assertTrue(result.mediosHabilitados().isEmpty());
        assertFalse(result.domicilioDisponible());
        assertTrue(result.vendedorPuntoFijo());
    }

    @Test
    void enablesCashAndBankTransferWhenConfigured() {
        ConfiguracionPago configuration = new ConfiguracionPago(
                VENDOR_ID, true, account(), null, null);

        var result = new ConsultarMediosPagoUseCase(repository(Optional.of(configuration)),
                vendorType(TipoVendedor.PUNTO_FIJO)).consultar(VENDOR_ID);

        assertEquals(java.util.List.of("EFECTIVO", "CUENTA_BANCARIA"),
                result.mediosHabilitados());
        assertTrue(result.domicilioDisponible());
    }

    @Test
    void ambulantSellerCannotOfferHomeDeliveryEvenWithBankAccount() {
        ConfiguracionPago configuration = new ConfiguracionPago(
                VENDOR_ID, false, account(), null, null);

        var result = new ConsultarMediosPagoUseCase(repository(Optional.of(configuration)),
                vendorType(TipoVendedor.AMBULANTE)).consultar(VENDOR_ID);

        assertEquals(java.util.List.of("CUENTA_BANCARIA"), result.mediosHabilitados());
        assertFalse(result.domicilioDisponible());
        assertFalse(result.vendedorPuntoFijo());
    }

    private static ConfiguracionPagoRepositoryPort repository(Optional<ConfiguracionPago> result) {
        return new ConfiguracionPagoRepositoryPort() {
            @Override
            public ConfiguracionPago save(ConfiguracionPago configuration) {
                return configuration;
            }

            @Override
            public Optional<ConfiguracionPago> findByVendedorId(UUID vendedorId) {
                return result;
            }
        };
    }

    private static ConsultarTipoVendedorPort vendorType(TipoVendedor type) {
        return ignored -> Optional.of(type);
    }

    private static CuentaBancaria account() {
        return new CuentaBancaria("Banco", TipoCuentaBancaria.AHORROS, "123456");
    }
}
