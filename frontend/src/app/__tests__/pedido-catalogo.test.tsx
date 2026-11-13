import { fireEvent, render, waitFor } from '@testing-library/react-native';
import * as Crypto from 'expo-crypto';

import { PedidoCatalogo } from '../../components/PedidoCatalogo';
import { ApiError } from '../../lib/api';
import {
  cotizarPedido,
  crearPedido,
  type CotizacionPedido,
  type PedidoCreado,
} from '../../lib/pedidos';
import type { Producto, ProductosPage } from '../../lib/productos';

jest.mock('expo-router', () => ({
  router: { push: jest.fn(), replace: jest.fn() },
}));

jest.mock('expo-crypto', () => ({ randomUUID: jest.fn() }));

jest.mock('expo-location', () => ({
  Accuracy: { Balanced: 3 },
  requestForegroundPermissionsAsync: jest.fn(),
  getCurrentPositionAsync: jest.fn(),
}));

jest.mock('../../lib/pedidos', () => ({
  cotizarPedido: jest.fn(),
  crearPedido: jest.fn(),
}));

const mango: Producto = {
  id: 'mango-id',
  emprendimientoId: 'business-id',
  nombre: 'Mango',
  precio: 3500,
  unidad: 'KG',
  descripcion: null,
  cantidadDisponible: 5,
  estado: 'DISPONIBLE',
  soloReserva: false,
  updatedAt: '2026-11-12T10:00:00Z',
  version: 0,
};

const page: ProductosPage = {
  content: [mango],
  page: 0,
  size: 20,
  totalElements: 1,
  emprendimiento: { id: 'business-id', nombre: 'Frutas de Ana' },
};

const deliveryQuote: CotizacionPedido = {
  items: [{
    productoId: 'mango-id',
    nombre: 'Mango',
    unidad: 'KG',
    cantidad: 1,
    precioUnitario: 3500,
    subtotal: 3500,
    estadoProducto: 'DISPONIBLE',
  }],
  subtotal: 3500,
  tarifaDomicilio: 3050,
  total: 6550,
  modalidadesDisponibles: ['RECOGIDA', 'DOMICILIO'],
  mediosPagoDisponibles: ['CUENTA_BANCARIA'],
  pagoDomicilio: {
    medio: 'CUENTA_BANCARIA',
    momento: 'AL_SER_ACEPTADO',
    total: 6550,
  },
  advertencias: [],
};

const createdOrder: PedidoCreado = {
  id: 'order-id',
  estado: 'PENDIENTE',
  modalidad: 'DOMICILIO',
  reserva: null,
  medioPago: 'CUENTA_BANCARIA',
  items: deliveryQuote.items,
  subtotal: 3500,
  tarifaDomicilio: 3050,
  total: 6550,
  ubicacionEntrega: {
    latitud: 4.711,
    longitud: -74.0721,
    direccion: 'Carrera 7 # 10-20',
    referencia: null,
  },
  pagoDomicilio: deliveryQuote.pagoDomicilio,
  createdAt: '2026-11-12T15:00:00Z',
};

function futureLocalDateTime() {
  const date = new Date(Date.now() + 24 * 60 * 60 * 1000);
  const pad = (part: number) => String(part).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function renderOrder(vendor: 'AMBULANTE' | 'PUNTO_FIJO' = 'PUNTO_FIJO', product = mango) {
  return render(
    <PedidoCatalogo
      emprendimientoId="business-id"
      tipoVendedor={vendor}
      productos={[product]}
      enabled
      pageData={page}
      pageBusy={false}
      onPage={jest.fn()}
    />,
  );
}

beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(Crypto.randomUUID).mockReturnValue('stable-order-key');
  jest.mocked(cotizarPedido).mockResolvedValue(deliveryQuote);
  jest.mocked(crearPedido).mockResolvedValue(createdOrder);
});

it('quotes and confirms delivery as a bank transfer with the payment notice', async () => {
  const view = await renderOrder();
  await fireEvent.press(view.getByRole('button', { name: 'Agregar Mango al pedido' }));
  await fireEvent.press(view.getByRole('button', { name: 'Continuar con la entrega' }));
  await fireEvent.press(view.getByRole('radio', { name: 'Domicilio' }));
  await fireEvent.changeText(view.getByLabelText('Dirección de entrega'), 'Carrera 7 # 10-20');
  await fireEvent.changeText(view.getByLabelText('Latitud'), '4.711');
  await fireEvent.changeText(view.getByLabelText('Longitud'), '-74.0721');
  await fireEvent.press(view.getByRole('button', { name: 'Cotizar pedido' }));

  await waitFor(() => {
    expect(cotizarPedido).toHaveBeenCalledWith({
      emprendimientoId: 'business-id',
      modalidad: 'DOMICILIO',
      items: [{ productoId: 'mango-id', cantidad: 1 }],
      ubicacionEntrega: {
        latitud: 4.711,
        longitud: -74.0721,
        direccion: 'Carrera 7 # 10-20',
        referencia: null,
      },
    });
    expect(view.getByText(/cuando el vendedor acepte el pedido/)).toBeTruthy();
  });

  await fireEvent.press(view.getByRole('button', { name: 'Confirmar pedido' }));
  await waitFor(() => {
    expect(crearPedido).toHaveBeenCalledWith(
      {
        emprendimientoId: 'business-id',
        modalidad: 'DOMICILIO',
        items: [{ productoId: 'mango-id', cantidad: 1 }],
        ubicacionEntrega: {
          latitud: 4.711,
          longitud: -74.0721,
          direccion: 'Carrera 7 # 10-20',
          referencia: null,
        },
        medioPago: 'CUENTA_BANCARIA',
        totalEsperado: 6550,
      },
      expect.any(String),
    );
    expect(view.getByText('Pedido enviado')).toBeTruthy();
    expect(view.getByText('Estado: PENDIENTE')).toBeTruthy();
  });
});

it('includes a future reservation and accepts an exhausted product for server quotation', async () => {
  const exhausted: Producto = {
    ...mango,
    cantidadDisponible: 0,
    estado: 'AGOTADO',
    soloReserva: true,
  };
  const reservationQuote = { ...deliveryQuote, tarifaDomicilio: null, total: 3500 };
  jest.mocked(cotizarPedido).mockResolvedValue(reservationQuote);
  const view = await renderOrder('PUNTO_FIJO', exhausted);
  await fireEvent.press(view.getByRole('button', { name: 'Agregar Mango al pedido' }));
  await fireEvent.press(view.getByRole('button', { name: 'Continuar con la entrega' }));
  await fireEvent.press(view.getByRole('radio', { name: 'Pedido inmediato' }));
  await fireEvent.press(view.getByRole('radio', { name: 'Reserva' }));
  await fireEvent.changeText(
    view.getByLabelText('Fecha y hora acordadas (AAAA-MM-DDTHH:mm)'),
    futureLocalDateTime(),
  );
  await fireEvent.press(view.getByRole('button', { name: 'Cotizar pedido' }));

  await waitFor(() => {
    expect(cotizarPedido).toHaveBeenCalledWith(
      expect.objectContaining({
        emprendimientoId: 'business-id',
        modalidad: 'RECOGIDA',
        items: [{ productoId: 'mango-id', cantidad: 1 }],
        reserva: expect.any(String),
      }),
    );
  });
});

it('removes domicile from the choices when the server reports no bank account', async () => {
  jest.mocked(cotizarPedido).mockRejectedValue(
    new ApiError('Este vendedor no ofrece domicilio.', 422, 'BANK_ACCOUNT_REQUIRED'),
  );
  const view = await renderOrder();
  await fireEvent.press(view.getByRole('button', { name: 'Agregar Mango al pedido' }));
  await fireEvent.press(view.getByRole('button', { name: 'Continuar con la entrega' }));
  await fireEvent.press(view.getByRole('radio', { name: 'Domicilio' }));
  await fireEvent.changeText(view.getByLabelText('Dirección de entrega'), 'Carrera 7 # 10-20');
  await fireEvent.changeText(view.getByLabelText('Latitud'), '4.711');
  await fireEvent.changeText(view.getByLabelText('Longitud'), '-74.0721');
  await fireEvent.press(view.getByRole('button', { name: 'Cotizar pedido' }));

  await waitFor(() => {
    expect(view.getByText('Este vendedor no ofrece domicilio.')).toBeTruthy();
    expect(view.queryByRole('radio', { name: 'Domicilio' })).toBeNull();
    expect(view.getByRole('radio', { name: 'Recogida en el punto' })).toBeTruthy();
  });
});

it('re-quotes after a total conflict and retries with a new idempotency key', async () => {
  jest.mocked(Crypto.randomUUID)
    .mockReturnValueOnce('first-order-key')
    .mockReturnValueOnce('second-order-key');
  const firstQuote: CotizacionPedido = {
    ...deliveryQuote,
    tarifaDomicilio: null,
    total: 3500,
    modalidadesDisponibles: ['RECOGIDA'],
    mediosPagoDisponibles: ['EFECTIVO'],
    pagoDomicilio: null,
  };
  const refreshedQuote: CotizacionPedido = {
    ...firstQuote,
    subtotal: 4000,
    total: 4000,
    items: [{ ...firstQuote.items[0], precioUnitario: 4000, subtotal: 4000 }],
  };
  jest.mocked(cotizarPedido)
    .mockResolvedValueOnce(firstQuote)
    .mockResolvedValueOnce(refreshedQuote);
  jest.mocked(crearPedido)
    .mockRejectedValueOnce(
      new ApiError('El total cambió desde que revisaste la cotización.', 409, 'CONFLICT'),
    )
    .mockResolvedValueOnce(createdOrder);

  const view = await renderOrder();
  await fireEvent.press(view.getByRole('button', { name: 'Agregar Mango al pedido' }));
  await fireEvent.press(view.getByRole('button', { name: 'Continuar con la entrega' }));
  await fireEvent.press(view.getByRole('button', { name: 'Cotizar pedido' }));
  await waitFor(() => expect(view.getByText('Total: $3.500 COP')).toBeTruthy());
  await fireEvent.press(view.getByRole('button', { name: 'Confirmar pedido' }));
  await waitFor(() =>
    expect(view.getByText('El total cambió desde que revisaste la cotización.')).toBeTruthy(),
  );

  await fireEvent.press(view.getByRole('button', { name: 'Cotizar pedido' }));
  await waitFor(() => expect(view.getByText('Total: $4.000 COP')).toBeTruthy());
  await fireEvent.press(view.getByRole('button', { name: 'Confirmar pedido' }));

  await waitFor(() => expect(crearPedido).toHaveBeenCalledTimes(2));
  const firstKey = jest.mocked(crearPedido).mock.calls[0][1];
  const secondKey = jest.mocked(crearPedido).mock.calls[1][1];
  expect(firstKey).not.toBe(secondKey);
  expect(jest.mocked(crearPedido).mock.calls[1][0].totalEsperado).toBe(4000);
});

