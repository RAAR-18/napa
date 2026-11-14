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

export type MedioPago = 'EFECTIVO' | 'CUENTA_BANCARIA';
export type EstadoPago = 'PENDIENTE_CONFIRMACION' | 'REGISTRADO' | 'CONFIRMADO';
export type TipoPago = 'PEDIDO' | 'TARIFA_DOMICILIO';

export const estadoPagoNombres: Record<EstadoPago, string> = {
  PENDIENTE_CONFIRMACION: 'Pendiente de confirmación',
  REGISTRADO: 'Registrado',
  CONFIRMADO: 'Confirmado',
};

export const medioPagoNombres: Record<MedioPago, string> = {
  EFECTIVO: 'Efectivo',
  CUENTA_BANCARIA: 'Cuenta bancaria',
};

export const tipoPagoNombres: Record<TipoPago, string> = {
  PEDIDO: 'Pago de pedido',
  TARIFA_DOMICILIO: 'Tarifa de domicilio',
};

/** RF-43. `cuentaDomiciliario` solo llega al vendedor y con el domicilio entregado. */
export type EstadoPagoTarifa = {
  domicilioId: string;
  pagoId: string | null;
  estado: 'SIN_REGISTRAR' | 'REGISTRADO' | 'CONFIRMADO';
  monto: number;
  medio: 'CUENTA_BANCARIA';
  registradoAt: string | null;
  confirmadoAt: string | null;
  cuentaDomiciliario: CuentaBancaria | null;
};

function pagoTarifaPath(domicilioId: string, suffix = '') {
  return `/api/v1/domicilios/${encodeURIComponent(domicilioId)}/pago-tarifa${suffix}`;
}

export function consultarPagoTarifa(domicilioId: string) {
  return withValidSession((session) =>
    requestJson<EstadoPagoTarifa>(pagoTarifaPath(domicilioId), {
      method: 'GET',
      headers: jsonHeaders({ accessToken: session.accessToken }),
    }),
  );
}

/** El vendedor deja constancia de que transfirió la tarifa (sin cuerpo: medio y monto los fija el servidor). */
export function registrarPagoTarifa(domicilioId: string, idempotencyKey: string) {
  return withValidSession((session) =>
    requestJson<EstadoPagoTarifa>(pagoTarifaPath(domicilioId), {
      method: 'POST',
      headers: jsonHeaders({ accessToken: session.accessToken, idempotencyKey }),
    }),
  );
}

/** El domiciliario confirma que le llegó la transferencia. */
export function confirmarPagoTarifa(domicilioId: string, idempotencyKey: string) {
  return withValidSession((session) =>
    requestJson<EstadoPagoTarifa>(pagoTarifaPath(domicilioId, '/confirmar'), {
      method: 'POST',
      headers: jsonHeaders({ accessToken: session.accessToken, idempotencyKey }),
    }),
  );
}

const CUENTA_DOMICILIARIO_PATH = '/api/v1/domiciliarios/me/configuracion-pago';

/** El número llega enmascarado salvo los últimos 4 dígitos; los tres campos son nulos si nunca la registró. */
export type CuentaDomiciliario = {
  banco: string | null;
  tipoCuenta: TipoCuentaBancaria | null;
  numeroCuenta: string | null;
};

export function obtenerCuentaDomiciliario() {
  return withValidSession((session) =>
    requestJson<CuentaDomiciliario>(CUENTA_DOMICILIARIO_PATH, {
      method: 'GET',
      headers: jsonHeaders({ accessToken: session.accessToken }),
    }),
  );
}

export function guardarCuentaDomiciliario(cuenta: CuentaBancaria) {
  return withValidSession((session) =>
    requestJson<CuentaDomiciliario>(CUENTA_DOMICILIARIO_PATH, {
      method: 'PUT',
      headers: jsonHeaders({ accessToken: session.accessToken }),
      body: JSON.stringify(cuenta),
    }),
  );
}

export type FiltrosHistorialPagos = {
  medio?: MedioPago;
  estado?: EstadoPago;
  tipo?: TipoPago;
  desde?: string;
  hasta?: string;
  page?: number;
  size?: number;
  sort?: 'createdAt,desc' | 'createdAt,asc' | 'monto,desc' | 'monto,asc';
};

type UsuarioPago = { id: string; nombre: string | null } | null;

/** RF-40 (cliente, vendedor, domiciliario) y RF-41 (administrador, con los usuarios involucrados). */
export type PagoHistorial = {
  id: string;
  tipo: TipoPago;
  monto: number;
  medio: MedioPago;
  estado: EstadoPago;
  createdAt: string;
  confirmadoAt: string | null;
  pedidoId: string | null;
  domicilioId: string | null;
  modalidad?: 'ENTREGA_DIRECTA' | 'RECOGIDA' | 'DOMICILIO' | null;
  comprador?: UsuarioPago;
  vendedor?: UsuarioPago;
  domiciliario?: UsuarioPago;
};

export type HistorialPagosPagina = {
  content: PagoHistorial[];
  page: number;
  size: number;
  totalElements: number;
};

export function listarPagos(filters: FiltrosHistorialPagos = {}) {
  const query = new URLSearchParams();
  Object.entries(filters).forEach(([name, value]) => {
    if (value !== undefined) query.set(name, String(value));
  });
  const queryString = query.toString();
  return withValidSession((session) =>
    requestJson<HistorialPagosPagina>(`/api/v1/pagos${queryString ? `?${queryString}` : ''}`, {
      method: 'GET',
      headers: jsonHeaders({ accessToken: session.accessToken }),
    }),
  );
}
