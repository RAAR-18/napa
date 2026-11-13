import { jsonHeaders, requestJson } from './api';
import { withValidSession } from './auth';

const CONFIGURACION_PAGO_PATH = '/api/v1/vendedores/me/configuracion-pago';

export type TipoCuentaBancaria = 'AHORROS' | 'CORRIENTE';

export type CuentaBancaria = {
  banco: string;
  tipoCuenta: TipoCuentaBancaria;
  numeroCuenta: string;
};

export type ConfiguracionPago = {
  aceptaEfectivo: boolean;
  cuentaBancaria: CuentaBancaria | null;
  mediosHabilitados: ('EFECTIVO' | 'CUENTA_BANCARIA')[];
  domicilioDisponible?: boolean;
  avisoDomicilio?: string;
};

export type ConfiguracionPagoInput = Pick<ConfiguracionPago, 'aceptaEfectivo'> & {
  cuentaBancaria: CuentaBancaria | null;
};

export function obtenerConfiguracionPago(): Promise<ConfiguracionPago> {
  return withValidSession((session) =>
    requestJson<ConfiguracionPago>(CONFIGURACION_PAGO_PATH, {
      method: 'GET',
      headers: jsonHeaders({ accessToken: session.accessToken }),
    }),
  );
}

export function guardarConfiguracionPago(
  input: ConfiguracionPagoInput,
): Promise<ConfiguracionPago> {
  const body = JSON.stringify(input);
  return withValidSession((session) =>
    requestJson<ConfiguracionPago>(CONFIGURACION_PAGO_PATH, {
      method: 'PUT',
      headers: jsonHeaders({ accessToken: session.accessToken }),
      body,
    }),
  );
}
