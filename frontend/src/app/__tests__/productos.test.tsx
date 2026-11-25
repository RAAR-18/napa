import { act, fireEvent, render, waitFor } from '@testing-library/react-native';
import { selectDateTime } from '../../test-utils/select-date-time';
import { router, useLocalSearchParams } from 'expo-router';
import { AppState, type AppStateStatus } from 'react-native';

import CatalogoScreen from '../catalogo';
import MisProductosScreen from '../mis-productos';
import NuevoProductoScreen from '../nuevo-producto';
import EditarProductoScreen from '../editar-producto';
import { ApiError, NetworkError } from '../../lib/api';
import { clearSession, getValidSession, readSession } from '../../lib/auth';
import { guardarBorradorPedido, guardarCarritoEncolado, leerBorradorPedido, leerCarritoEncolado, leerCotizacionOffline } from '../../lib/pedido-offline';
import { outbox, type AccionPendiente } from '../../database/outbox';
import { cotizarPedido, crearPedido } from '../../lib/pedidos';
import { borrarCatalogoGuardado, leerPaginaCatalogo, listarPaginasGuardadasCatalogo } from '../../lib/catalogo-cache';
import { leerPaginaInventario, leerProductoInventario } from '../../lib/inventario-cache';
import { createProducto, deleteProducto, editProducto, getMiProducto, listCatalogo, listMisProductos, updateInventario, type Producto, type ProductosPage } from '../../lib/productos';

jest.mock('expo-router', () => ({
  router: { push: jest.fn(), replace: jest.fn() },
  useLocalSearchParams: jest.fn(() => ({
    emprendimientoId: 'business-id',
    tipoVendedor: 'AMBULANTE',
    productoId: 'product-id',
  })),
  useFocusEffect: (effect: () => void | (() => void)) => {
    const React = jest.requireActual<typeof import('react')>('react');
    React.useEffect(effect, [effect]);
  },
}));
jest.mock('../../lib/uuid', () => ({ randomUUID: () => 'registro-idempotente' }));
jest.mock('../../database/outbox', () => ({
  outbox: {
    propietario: jest.fn().mockResolvedValue('client-id'),
    listar: jest.fn().mockResolvedValue([]), suscribir: () => () => {},
    encolar: jest.fn(), corregirFallida: jest.fn().mockResolvedValue(undefined), procesar: jest.fn().mockResolvedValue(undefined),
  },
}));
jest.mock('../../lib/auth', () => ({
  getSessionGeneration: () => 0,
  getValidSession: jest.fn(),
  readSession: jest.fn(),
  clearSession: jest.fn(),
}));
jest.mock('../../lib/pedidos', () => ({
  ...jest.requireActual('../../lib/pedidos'),
  cotizarPedido: jest.fn(), crearPedido: jest.fn(),
}));
jest.mock('../../lib/pedido-offline', () => ({
  ...jest.requireActual('../../lib/pedido-offline'),
  guardarBorradorPedido: jest.fn().mockResolvedValue(undefined),
  guardarCarritoEncolado: jest.fn().mockResolvedValue(undefined),
  leerCotizacionOffline: jest.fn().mockResolvedValue(null),
  leerCarritoEncolado: jest.fn().mockResolvedValue(null),
  guardarCotizacionOffline: jest.fn().mockResolvedValue(undefined),
  borrarCarritoEncolado: jest.fn().mockResolvedValue(undefined),
  leerBorradorPedido: jest.fn().mockResolvedValue(null),
  leerUltimaDireccion: jest.fn().mockResolvedValue(null),
  borrarBorradorPedido: jest.fn().mockResolvedValue(undefined),
}));
jest.mock('../../lib/catalogo-cache', () => ({
  guardarPaginaCatalogo: jest.fn(),
  leerPaginaCatalogo: jest.fn(),
  listarPaginasGuardadasCatalogo: jest.fn(),
  borrarCatalogoGuardado: jest.fn(),
}));
jest.mock('../../lib/inventario-cache', () => ({
  guardarPaginaInventario: jest.fn(),
  leerPaginaInventario: jest.fn(),
  leerProductoInventario: jest.fn(),
  borrarInventarioGuardado: jest.fn(),
}));
jest.mock('../../lib/productos', () => ({
  ...jest.requireActual('../../lib/productos'),
  createProducto: jest.fn(), listCatalogo: jest.fn(), listMisProductos: jest.fn(), updateInventario: jest.fn(),
  deleteProducto: jest.fn(), editProducto: jest.fn(), getMiProducto: jest.fn(),
}));

const seller = {
  accessToken: 'access', refreshToken: 'refresh', rol: 'VENDEDOR_AMBULANTE',
  expiresIn: 900, expiresAt: Date.now() + 900000, tokenType: 'Bearer',
};
const available: Producto = {
  id: 'product-id', emprendimientoId: 'business-id', nombre: 'Mango', precio: 3500, unidad: 'KG',
  descripcion: 'Mango fresco', cantidadDisponible: 12.5, estado: 'DISPONIBLE', soloReserva: false,
  updatedAt: '2026-11-11T10:00:00Z', version: 0,
};
const exhausted: Producto = { ...available, cantidadDisponible: 0, estado: 'AGOTADO', soloReserva: true, updatedAt: '2026-11-11T10:01:00Z' };
const page: ProductosPage = { content: [available], page: 0, size: 20, totalElements: 1, emprendimiento: { id: 'business-id', nombre: 'Frutas de Ana' } };

beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(getValidSession).mockResolvedValue(seller);
  jest.mocked(readSession).mockResolvedValue(seller);
  jest.mocked(listMisProductos).mockResolvedValue(page);
  jest.mocked(listCatalogo).mockResolvedValue(page);
  jest.mocked(createProducto).mockResolvedValue(available);
  jest.mocked(updateInventario).mockResolvedValue(exhausted);
  jest.mocked(deleteProducto).mockResolvedValue();
  jest.mocked(editProducto).mockResolvedValue(available);
  jest.mocked(getMiProducto).mockResolvedValue(available);
});
afterEach(() => jest.restoreAllMocks());

it('validates initial stock and publishes a decimal product without requiring a description', async () => {
  const view = await render(<NuevoProductoScreen />);
  await waitFor(() => expect(view.getByLabelText('Nombre del producto')).toBeTruthy());
  await fireEvent.changeText(view.getByLabelText('Nombre del producto'), '  Mango  ');
  await fireEvent.changeText(view.getByLabelText('Precio por unidad (COP)'), '3500');
  await fireEvent.changeText(view.getByLabelText('Cantidad disponible'), '0');
  await fireEvent.press(view.getByRole('button', { name: 'Publicar producto' }));
  await waitFor(() => expect(view.getByText('La cantidad inicial debe ser mayor que cero y tener hasta dos decimales.')).toBeTruthy());
  expect(createProducto).not.toHaveBeenCalled();
  await fireEvent.changeText(view.getByLabelText('Cantidad disponible'), '12,5');
  await fireEvent.press(view.getByRole('radio', { name: 'Kilogramo' }));
  await fireEvent.press(view.getByRole('button', { name: 'Publicar producto' }));
  await waitFor(() => expect(createProducto).toHaveBeenCalledWith({
    nombre: 'Mango', precio: 3500, unidad: 'KG', descripcion: null, cantidadDisponible: 12.5,
    emprendimientoId: 'business-id',
  }, 'registro-idempotente', undefined));
  expect(router.replace).toHaveBeenCalledWith('/mis-productos');
}, 60000);

it('preserves the form when the backend rejects a duplicate product', async () => {
  jest.mocked(createProducto).mockRejectedValue(new ApiError('Ya tienes un producto con ese nombre.', 409, 'CONFLICT'));
  const view = await render(<NuevoProductoScreen />);
  await waitFor(() => expect(view.getByLabelText('Nombre del producto')).toBeTruthy());
  await fireEvent.changeText(view.getByLabelText('Nombre del producto'), 'Mango');
  await fireEvent.changeText(view.getByLabelText('Precio por unidad (COP)'), '3500');
  await fireEvent.changeText(view.getByLabelText('Cantidad disponible'), '2');
  await fireEvent.press(view.getByRole('button', { name: 'Publicar producto' }));
  await waitFor(() => expect(view.getByText('Ya tienes un producto con ese nombre.')).toBeTruthy());
  expect(view.getByLabelText('Nombre del producto').props.value).toBe('Mango');
  expect(router.replace).not.toHaveBeenCalled();
});

it('does not expose the publishing form to a client', async () => {
  jest.mocked(getValidSession).mockResolvedValue({ ...seller, rol: 'CLIENTE' });
  const view = await render(<NuevoProductoScreen />);
  await waitFor(() => expect(view.getByText('Solo los vendedores pueden publicar productos.')).toBeTruthy());
  expect(view.queryByRole('button', { name: 'Publicar producto' })).toBeNull();
});

it('marks a product as exhausted and reloads the current stock after a successful save', async () => {
  jest.mocked(listMisProductos).mockResolvedValueOnce(page).mockResolvedValue({ ...page, content: [exhausted] });
  const view = await render(<MisProductosScreen />);
  await waitFor(() => expect(view.getByText('Mango')).toBeTruthy());
  await fireEvent.press(view.getByRole('button', { name: 'Marcar Mango como agotado' }));
  await waitFor(() => expect(updateInventario).toHaveBeenCalledWith('product-id', 0));
  await waitFor(() => expect(view.getByText('Agotado · Solo reserva')).toBeTruthy());
  expect(view.getByText('Catálogo actualizado.')).toBeTruthy();
});

it('rejects negative inventory locally and keeps the original product visible', async () => {
  const view = await render(<MisProductosScreen />);
  await waitFor(() => expect(view.getByLabelText('Nueva cantidad de Mango')).toBeTruthy());
  await fireEvent.changeText(view.getByLabelText('Nueva cantidad de Mango'), '-1');
  await fireEvent.press(view.getByRole('button', { name: 'Guardar inventario de Mango' }));
  await waitFor(() => expect(view.getByText(/La cantidad debe ser cero o mayor/)).toBeTruthy());
  expect(updateInventario).not.toHaveBeenCalled();
  expect(view.getByText('Disponible')).toBeTruthy();
});

it('guides a seller without a business to create one first', async () => {
  jest.mocked(listMisProductos).mockResolvedValue({ ...page, emprendimiento: null, content: [], totalElements: 0 });
  const view = await render(<MisProductosScreen />);
  await waitFor(() => expect(view.getByText('Primero crea tu emprendimiento para publicar productos.')).toBeTruthy());
  await fireEvent.press(view.getByRole('button', { name: 'Crear mi emprendimiento' }));
  expect(router.push).toHaveBeenCalledWith('/emprendimiento');
  expect(view.queryByRole('button', { name: 'Agregar producto' })).toBeNull();
});

it('shows exhausted catalog products as Solo reserva without seller actions', async () => {
  jest.mocked(getValidSession).mockResolvedValue({ ...seller, rol: 'CLIENTE' });
  jest.mocked(listCatalogo).mockResolvedValue({ ...page, content: [exhausted] });
  const view = await render(<CatalogoScreen />);
  await waitFor(() => expect(view.getByText('Solo reserva')).toBeTruthy());
  expect(view.getByText('Frutas de Ana')).toBeTruthy();
  expect(view.queryByLabelText('Nueva cantidad de Mango')).toBeNull();
  expect(listCatalogo).toHaveBeenCalledWith('business-id', 0, 'nombre,asc');
  expect(listMisProductos).not.toHaveBeenCalled();
});

it('shows an empty catalog without fabricating products', async () => {
  jest.mocked(getValidSession).mockResolvedValue({ ...seller, rol: 'CLIENTE' });
  jest.mocked(listCatalogo).mockResolvedValue({ ...page, content: [], totalElements: 0 });
  const view = await render(<CatalogoScreen />);
  await waitFor(() => expect(view.getByText('Este emprendimiento aún no tiene productos publicados.')).toBeTruthy());
});

it('refreshes catalog availability when the app returns to the foreground', async () => {
  jest.mocked(getValidSession).mockResolvedValue({ ...seller, rol: 'CLIENTE' });
  jest.mocked(listCatalogo).mockResolvedValueOnce(page).mockResolvedValue({ ...page, content: [exhausted] });
  let foreground: ((state: AppStateStatus) => void) | undefined;
  const remove = jest.fn();
  jest.spyOn(AppState, 'addEventListener').mockImplementation((_event, listener) => {
    foreground = listener;
    return { remove };
  });
  const view = await render(<CatalogoScreen />);
  await waitFor(() => expect(view.getByText('Disponible')).toBeTruthy());
  await act(async () => { foreground?.('active'); });
  await waitFor(() => expect(view.getByText('Solo reserva')).toBeTruthy());
  expect(getValidSession).toHaveBeenCalledTimes(2);
  await view.unmount();
  expect(remove).toHaveBeenCalled();
});

it('clears an expired session and redirects instead of showing private products', async () => {
  jest.mocked(getValidSession).mockRejectedValue(new ApiError('Sesión vencida', 401, 'UNAUTHORIZED'));
  const view = await render(<MisProductosScreen />);
  await waitFor(() => expect(router.replace).toHaveBeenCalledWith('/login'));
  expect(clearSession).toHaveBeenCalled();
  expect(listMisProductos).not.toHaveBeenCalled();
  expect(view.queryByText('Mango')).toBeNull();
});

it('opens the editor for the selected owner product', async () => {
  const view = await render(<MisProductosScreen />);
  await waitFor(() => expect(view.getByRole('button', { name: 'Editar Mango' })).toBeTruthy());
  await fireEvent.press(view.getByRole('button', { name: 'Editar Mango' }));
  expect(router.push).toHaveBeenCalledWith({ pathname: '/editar-producto', params: { productoId: 'product-id' } });
});

it('loads current values and saves an edit without sending stock or unit', async () => {
  const view = await render(<EditarProductoScreen />);
  await waitFor(() => expect(view.getByLabelText('Nombre del producto').props.value).toBe('Mango'));
  await fireEvent.changeText(view.getByLabelText('Nombre del producto'), 'Mango dulce');
  await fireEvent.changeText(view.getByLabelText('Precio por unidad (COP)'), '4500');
  await fireEvent.changeText(view.getByLabelText('Descripción (opcional)'), '');
  await fireEvent.press(view.getByRole('button', { name: 'Guardar cambios' }));
  await waitFor(() => expect(editProducto).toHaveBeenCalledWith('product-id', {
    nombre: 'Mango dulce', precio: 4500, descripcion: null,
  }, 0));
  expect(router.replace).toHaveBeenCalledWith('/mis-productos');
  expect(updateInventario).not.toHaveBeenCalled();
}, 15000);

it('keeps the draft on conflict and lets the seller reload the new version explicitly', async () => {
  jest.mocked(editProducto).mockRejectedValue(new ApiError('El producto cambió mientras editabas.', 409, 'CONFLICT'));
  jest.mocked(getMiProducto).mockResolvedValueOnce(available).mockResolvedValue({ ...available, nombre: 'Nombre actual', version: 4 });
  const view = await render(<EditarProductoScreen />);
  await waitFor(() => expect(view.getByLabelText('Nombre del producto')).toBeTruthy());
  await fireEvent.changeText(view.getByLabelText('Nombre del producto'), 'Mi cambio');
  await fireEvent.press(view.getByRole('button', { name: 'Guardar cambios' }));
  await waitFor(() => expect(view.getByText('El producto cambió mientras editabas.')).toBeTruthy());
  expect(view.getByLabelText('Nombre del producto').props.value).toBe('Mi cambio');
  await fireEvent.press(view.getByRole('button', { name: 'Recargar datos del producto' }));
  await waitFor(() => expect(view.getByLabelText('Nombre del producto').props.value).toBe('Nombre actual'));
  jest.mocked(editProducto).mockResolvedValue(available);
  await fireEvent.press(view.getByRole('button', { name: 'Guardar cambios' }));
  await waitFor(() => expect(editProducto).toHaveBeenLastCalledWith('product-id', {
    nombre: 'Nombre actual', precio: 3500, descripcion: 'Mango fresco',
  }, 4));
}, 15000);

it('rejects an invalid edit price locally and retains the form', async () => {
  const view = await render(<EditarProductoScreen />);
  await waitFor(() => expect(view.getByLabelText('Precio por unidad (COP)')).toBeTruthy());
  await fireEvent.changeText(view.getByLabelText('Precio por unidad (COP)'), '-5');
  await fireEvent.press(view.getByRole('button', { name: 'Guardar cambios' }));
  await waitFor(() => expect(view.getByText('El precio debe ser un entero positivo en pesos, sin puntos ni comas.')).toBeTruthy());
  expect(editProducto).not.toHaveBeenCalled();
  expect(view.getByLabelText('Precio por unidad (COP)').props.value).toBe('-5');
});

it('does not show private editing data when the API rejects ownership', async () => {
  jest.mocked(getMiProducto).mockRejectedValue(new ApiError('Solo puedes modificar tus propios productos.', 403, 'FORBIDDEN'));
  const view = await render(<EditarProductoScreen />);
  await waitFor(() => expect(view.getByText('Solo puedes modificar tus propios productos.')).toBeTruthy());
  expect(view.queryByRole('button', { name: 'Guardar cambios' })).toBeNull();
});

it('allows cancelling deletion without sending a request', async () => {
  const view = await render(<MisProductosScreen />);
  await waitFor(() => expect(view.getByRole('button', { name: 'Eliminar Mango' })).toBeTruthy());
  await fireEvent.press(view.getByRole('button', { name: 'Eliminar Mango' }));
  expect(view.getByRole('button', { name: 'Confirmar eliminación de Mango' })).toBeTruthy();
  expect(deleteProducto).not.toHaveBeenCalled();
  await fireEvent.press(view.getByRole('button', { name: 'Cancelar eliminación de Mango' }));
  expect(view.getByLabelText('Nueva cantidad de Mango')).toBeTruthy();
  expect(deleteProducto).not.toHaveBeenCalled();
});

it('removes the product only after confirmed deletion succeeds', async () => {
  let finish: (() => void) | undefined;
  jest.mocked(deleteProducto).mockReturnValue(new Promise<void>((resolve) => { finish = resolve; }));
  jest.mocked(listMisProductos).mockResolvedValueOnce(page).mockResolvedValue({ ...page, content: [], totalElements: 0 });
  const view = await render(<MisProductosScreen />);
  await waitFor(() => expect(view.getByRole('button', { name: 'Eliminar Mango' })).toBeTruthy());
  await fireEvent.press(view.getByRole('button', { name: 'Eliminar Mango' }));
  await fireEvent.press(view.getByRole('button', { name: 'Confirmar eliminación de Mango' }));
  expect(deleteProducto).toHaveBeenCalledTimes(1);
  expect(view.getByText('Mango')).toBeTruthy();
  await act(async () => { finish?.(); });
  await waitFor(() => expect(view.getByText('Aún no has publicado productos.')).toBeTruthy());
  expect(view.queryByText('Mango')).toBeNull();
});

it('keeps a product visible when deletion is blocked by pending orders', async () => {
  jest.mocked(deleteProducto).mockRejectedValue(new ApiError('Este producto tiene pedidos pendientes.', 409, 'HAS_PENDING_ORDERS'));
  const view = await render(<MisProductosScreen />);
  await waitFor(() => expect(view.getByRole('button', { name: 'Eliminar Mango' })).toBeTruthy());
  await fireEvent.press(view.getByRole('button', { name: 'Eliminar Mango' }));
  await fireEvent.press(view.getByRole('button', { name: 'Confirmar eliminación de Mango' }));
  await waitFor(() => expect(view.getByText('Este producto tiene pedidos pendientes.')).toBeTruthy());
  expect(view.getByText('Mango')).toBeTruthy();
  expect(listMisProductos).toHaveBeenCalledTimes(1);
});

it('returns to the last valid page after deleting its last product', async () => {
  const last = { ...available, id: 'last-id', nombre: 'Papaya' };
  jest.mocked(listMisProductos)
    .mockResolvedValueOnce({ ...page, totalElements: 21 })
    .mockResolvedValueOnce({ ...page, page: 1, totalElements: 21, content: [last] })
    .mockResolvedValueOnce({ ...page, page: 1, totalElements: 20, content: [] })
    .mockResolvedValue({ ...page, totalElements: 20 });
  const view = await render(<MisProductosScreen />);
  await waitFor(() => expect(view.getByRole('button', { name: 'Página siguiente' })).toBeTruthy());
  await fireEvent.press(view.getByRole('button', { name: 'Página siguiente' }));
  await waitFor(() => expect(view.getByText('Papaya')).toBeTruthy());
  await fireEvent.press(view.getByRole('button', { name: 'Eliminar Papaya' }));
  await fireEvent.press(view.getByRole('button', { name: 'Confirmar eliminación de Papaya' }));
  await waitFor(() => expect(view.getByText('Mango')).toBeTruthy());
  expect(listMisProductos).toHaveBeenLastCalledWith(0, 'nombre,asc');
}, 15000);

it('shows saved inventory in read-only mode when token renewal fails offline', async () => {
  jest.mocked(readSession).mockResolvedValue({
    ...seller, usuarioId: 'seller-id', expiresAt: 0,
  });
  jest.mocked(getValidSession).mockRejectedValue(new NetworkError('Sin conexión'));
  const updatedAt = Date.now();
  jest.mocked(leerPaginaInventario).mockResolvedValue({ data: page, updatedAt });

  const screen = await render(<MisProductosScreen />);

  await waitFor(() => expect(screen.getByText(/Cantidad guardada/)).toBeTruthy());
  expect(screen.getByText(/Datos guardados/)).toBeTruthy();
  expect(screen.getByRole('button', { name: 'Agregar producto' })).toBeTruthy();
  expect(screen.getByRole('button', { name: 'Guardar inventario de Mango' })).toBeTruthy();
  expect(screen.queryByRole('button', { name: 'Eliminar Mango' })).toBeNull();
  expect(leerPaginaInventario).toHaveBeenCalledWith('seller-id', 'nombre,asc', 0);
});

const clientOffline = { ...seller, rol: 'CLIENTE', usuarioId: 'client-id', expiresAt: 0 };

it('shows saved catalog and cart when token renewal fails offline', async () => {
  jest.mocked(readSession).mockResolvedValue(clientOffline);
  jest.mocked(getValidSession).mockRejectedValueOnce(new NetworkError('Sin conexión'));
  jest.mocked(leerPaginaCatalogo).mockResolvedValueOnce({
    data: { ...page, content: [exhausted] },
    updatedAt: Date.now(),
  });

  const view = await render(<CatalogoScreen />);

  await waitFor(() => expect(view.getByText(/Datos guardados/)).toBeTruthy());
  expect(view.getByText('Solo reserva')).toBeTruthy();
  expect(view.getByText('Arma tu pedido')).toBeTruthy();
  expect(leerPaginaCatalogo).toHaveBeenCalledWith('client-id', 'business-id', 'nombre,asc', 0);
}, 15000);

it('uses a saved quote and requires address confirmation before queuing direct delivery', async () => {
  jest.mocked(readSession).mockResolvedValue(clientOffline);
  jest.mocked(getValidSession).mockRejectedValue(new NetworkError('Sin conexión'));
  jest.mocked(leerPaginaCatalogo).mockResolvedValue({ data: page, updatedAt: Date.now() });
  const direccion = { direccion: 'Calle 15', latitud: 11.24, longitud: -74.21, referencia: null };
  jest.mocked(leerCotizacionOffline).mockResolvedValueOnce({ medios: ['EFECTIVO'], direccion });
  const view = await render(<CatalogoScreen />);

  await waitFor(() => expect(view.getByText('Arma tu pedido')).toBeTruthy());
  await fireEvent.press(view.getByRole('button', { name: 'Agregar Mango al pedido' }));
  await fireEvent.press(view.getByRole('button', { name: 'Continuar con la entrega' }));
  expect((await view.findByRole('radio', { name: 'Efectivo' })).props.accessibilityState.checked).toBe(true);
  const confirmar = () => view.getByRole('button', { name: 'Confirmar y guardar pedido pendiente' });
  expect(confirmar()).toBeDisabled();
  await fireEvent.press(view.getByRole('radio', { name: 'Confirmo esta dirección' }));
  await fireEvent.press(confirmar());

  await waitFor(() => expect(outbox.encolar).toHaveBeenCalledWith(expect.objectContaining({
    usuarioId: 'client-id', method: 'POST', path: '/api/v1/pedidos',
    idempotencyKey: 'registro-idempotente',
    body: {
      emprendimientoId: 'business-id', modalidad: 'ENTREGA_DIRECTA', medioPago: 'EFECTIVO',
      ubicacionEntrega: direccion, items: [{ productoId: 'product-id', cantidad: 1 }],
      totalEsperado: 3500, preciosUnitariosEsperados: { 'product-id': 3500 },
    },
  })));
  expect(guardarCarritoEncolado).toHaveBeenCalledTimes(1);
  expect(await view.findByText('Pedido guardado para envío')).toBeTruthy();
}, 15000);

it('saves a reservation as a draft even with a previously saved payment method', async () => {
  jest.mocked(readSession).mockResolvedValue(clientOffline);
  jest.mocked(getValidSession).mockRejectedValue(new NetworkError('Sin conexión'));
  jest.mocked(leerPaginaCatalogo).mockResolvedValue({ data: page, updatedAt: Date.now() });
  jest.mocked(leerCotizacionOffline).mockResolvedValueOnce({
    medios: ['EFECTIVO'], direccion: { direccion: 'Calle 15', latitud: 11.24, longitud: -74.21, referencia: null },
  });
  const view = await render(<CatalogoScreen />);

  await fireEvent.press(await view.findByRole('button', { name: 'Agregar Mango al pedido' }));
  await fireEvent.press(view.getByRole('button', { name: 'Continuar con la entrega' }));
  await fireEvent.press(view.getByRole('radio', { name: 'Reserva' }));
  await selectDateTime(view, 'Fecha y hora acordadas', '2027-01-01T15:30');
  expect(view.queryByRole('button', { name: 'Confirmar y guardar pedido pendiente' })).toBeNull();
  await fireEvent.press(view.getByRole('button', { name: 'Guardar borrador' }));

  await waitFor(() => expect(guardarBorradorPedido).toHaveBeenCalledWith(
    'client-id', 'business-id', expect.objectContaining({
      modalidad: 'ENTREGA_DIRECTA', reserva: true, fechaReserva: '2027-01-01T15:30',
      items: [{ producto: available, cantidad: 1 }],
    }),
  ));
  expect(outbox.encolar).not.toHaveBeenCalled();
}, 15000);

it('reopens a rejected cart and replaces the failed request only after requoting and confirmation', async () => {
  jest.mocked(readSession).mockResolvedValue(clientOffline);
  jest.mocked(getValidSession).mockResolvedValue(clientOffline);
  jest.mocked(useLocalSearchParams).mockReturnValue({
    emprendimientoId: 'business-id', tipoVendedor: 'AMBULANTE', pendienteId: 'failed-order',
  });
  const direccion = { direccion: 'Calle 15', latitud: 11.24, longitud: -74.21, referencia: null };
  const body = {
    emprendimientoId: 'business-id', modalidad: 'ENTREGA_DIRECTA',
    items: [{ productoId: 'product-id', cantidad: 1 }], ubicacionEntrega: direccion,
    medioPago: 'EFECTIVO', totalEsperado: 3500, preciosUnitariosEsperados: { 'product-id': 3500 },
  };
  const accion: AccionPendiente = {
    id: 'failed-order', usuarioId: 'client-id', method: 'POST', path: '/api/v1/pedidos',
    body: JSON.stringify(body), idempotencyKey: 'clave-rechazada', descripcion: 'Pedido a Frutas de Ana',
    status: 'FALLIDA', attempts: 1, lastError: 'Cambió el precio de un producto.', nextAttemptAt: 0, createdAt: 0,
  };
  jest.mocked(outbox.listar).mockResolvedValueOnce([accion]);
  jest.mocked(leerCarritoEncolado).mockResolvedValueOnce({
    items: [{ producto: available, cantidad: 1 }],
    modalidad: 'ENTREGA_DIRECTA', reserva: false, fechaReserva: '',
  });
  jest.mocked(cotizarPedido).mockResolvedValueOnce({
    items: [{ productoId: 'product-id', nombre: 'Mango', unidad: 'KG', cantidad: 1,
      precioUnitario: 4000, subtotal: 4000, estadoProducto: 'DISPONIBLE' }],
    subtotal: 4000, tarifaDomicilio: null, total: 4000, modalidadesDisponibles: ['ENTREGA_DIRECTA'],
    mediosPagoDisponibles: ['EFECTIVO'], pagoDomicilio: null, advertencias: [],
  });
  try {
    const view = await render(<CatalogoScreen />);
    await view.findByText('Cambió el precio de un producto.');
    await fireEvent.press(view.getByRole('button', { name: 'Continuar con la entrega' }));
    await fireEvent.press(view.getByRole('button', { name: 'Cotizar pedido' }));
    const confirmar = await view.findByRole('button', { name: 'Confirmar y reenviar pedido' });
    expect(outbox.corregirFallida).not.toHaveBeenCalled();
    await fireEvent.press(confirmar);
    await waitFor(() => expect(outbox.corregirFallida).toHaveBeenCalledWith(accion, expect.objectContaining({
      idempotencyKey: 'registro-idempotente',
      body: expect.objectContaining({ totalEsperado: 4000, preciosUnitariosEsperados: { 'product-id': 4000 } }),
    })));
    expect(crearPedido).not.toHaveBeenCalled();
  } finally {
    jest.mocked(useLocalSearchParams).mockImplementation(() => ({
      emprendimientoId: 'business-id', tipoVendedor: 'AMBULANTE', productoId: 'product-id',
    }));
  }
}, 15000);

it('offers a saved catalog page when the requested page is missing', async () => {
  jest.mocked(readSession).mockResolvedValue(clientOffline);
  jest.mocked(getValidSession).mockRejectedValue(new NetworkError('Sin conexión'));
  jest.mocked(leerPaginaCatalogo).mockResolvedValueOnce(null).mockResolvedValueOnce({
    data: { ...page, page: 1, totalElements: 21 },
    updatedAt: Date.now(),
  });
  jest.mocked(listarPaginasGuardadasCatalogo).mockResolvedValueOnce([1]);

  const view = await render(<CatalogoScreen />);
  await waitFor(() => expect(view.getByText(/Esta página del catálogo no está descargada/)).toBeTruthy());
  await fireEvent.press(view.getByText('Página guardada siguiente'));
  await waitFor(() => expect(view.getByText(/Datos guardados/)).toBeTruthy());
  expect(leerPaginaCatalogo).toHaveBeenLastCalledWith('client-id', 'business-id', 'nombre,asc', 1);
}, 15000);

it('invalidates the saved catalog of a business that the server no longer exposes', async () => {
  jest.mocked(readSession).mockResolvedValueOnce(clientOffline);
  jest.mocked(getValidSession).mockResolvedValueOnce(clientOffline);
  jest.mocked(listCatalogo).mockRejectedValueOnce(new ApiError('Emprendimiento no disponible', 404, 'NOT_FOUND'));

  const view = await render(<CatalogoScreen />);
  await waitFor(() => expect(view.getByText('Emprendimiento no disponible')).toBeTruthy());
  expect(borrarCatalogoGuardado).toHaveBeenCalledWith('client-id', 'business-id');
  expect(leerPaginaCatalogo).not.toHaveBeenCalled();
}, 15000);

it('queues a bound registration without waiting for the server', async () => {
  jest.mocked(readSession).mockResolvedValue({ ...seller, usuarioId: 'cuenta-A' });
  jest.mocked(outbox.procesar).mockImplementationOnce(() => new Promise(() => {}));
  const view = await render(<NuevoProductoScreen />);
  await waitFor(() => expect(view.getByLabelText('Nombre del producto')).toBeTruthy());
  await fireEvent.changeText(view.getByLabelText('Nombre del producto'), 'Mango');
  await fireEvent.changeText(view.getByLabelText('Precio por unidad (COP)'), '3500');
  await fireEvent.changeText(view.getByLabelText('Cantidad disponible'), '2');
  await fireEvent.press(view.getByRole('button', { name: 'Publicar producto' }));

  await waitFor(() => expect(outbox.encolar).toHaveBeenCalled());
  expect(createProducto).not.toHaveBeenCalled();
  expect(outbox.encolar).toHaveBeenCalledWith(expect.objectContaining({
    usuarioId: 'cuenta-A', path: '/api/v1/productos', idempotencyKey: 'registro-idempotente',
    body: expect.objectContaining({ nombre: 'Mango', emprendimientoId: 'business-id' }),
  }));
  await waitFor(() => expect(router.replace).toHaveBeenCalledWith('/mis-productos'));
}, 15000);

it('queues only the edited fields with the version from the saved product', async () => {
  jest.mocked(readSession).mockResolvedValue({ ...seller, usuarioId: 'vendedor' });
  jest.mocked(leerProductoInventario).mockResolvedValueOnce({ ...available, version: 7 });
  const view = await render(<EditarProductoScreen />);
  await waitFor(() => expect(view.getByLabelText('Precio por unidad (COP)')).toBeTruthy());
  await fireEvent.changeText(view.getByLabelText('Precio por unidad (COP)'), '4500');
  await fireEvent.press(view.getByRole('button', { name: 'Guardar cambios' }));
  await waitFor(() => expect(outbox.encolar).toHaveBeenCalledWith({
    usuarioId: 'vendedor', method: 'PATCH', path: '/api/v1/productos/product-id',
    body: { precio: 4500 }, ifMatch: '"7"', idempotencyKey: 'registro-idempotente',
    descripcion: 'Editar producto: Mango',
  }));
  expect(editProducto).not.toHaveBeenCalled();
}, 60000);

it('queues absolute stock from a saved product using the original version', async () => {
  jest.mocked(readSession).mockResolvedValue({ ...seller, usuarioId: 'vendedor' });
  jest.mocked(getValidSession).mockRejectedValue(new NetworkError('Sin conexión'));
  jest.mocked(leerPaginaInventario).mockResolvedValue({
    data: { ...page, content: [{ ...available, version: 9 }] }, updatedAt: Date.now(),
  });
  const view = await render(<MisProductosScreen />);
  await waitFor(() => expect(view.getByLabelText('Nueva cantidad de Mango')).toBeTruthy());
  await fireEvent.changeText(view.getByLabelText('Nueva cantidad de Mango'), '17,5');
  await fireEvent.press(view.getByRole('button', { name: 'Guardar inventario de Mango' }));
  await waitFor(() => expect(outbox.encolar).toHaveBeenCalledWith({
    usuarioId: 'vendedor', method: 'POST', path: '/api/v1/productos/product-id/inventario',
    body: { cantidadDisponible: 17.5 }, ifMatch: '"9"', idempotencyKey: 'registro-idempotente',
    descripcion: 'Ajustar inventario: Mango',
  }));
  expect(updateInventario).not.toHaveBeenCalled();
}, 60000);


it('automatically saves the cart and restores it after returning to the catalog', async () => {
  const cliente = { ...seller, rol: 'CLIENTE', usuarioId: 'client-id' };
  jest.mocked(readSession).mockResolvedValue(cliente);
  jest.mocked(getValidSession).mockResolvedValue(cliente);
  const view = await render(<CatalogoScreen />);
  await fireEvent.press(await view.findByRole('button', { name: 'Agregar Mango al pedido' }));
  await waitFor(() => expect(guardarBorradorPedido).toHaveBeenCalledWith('client-id', 'business-id',
    expect.objectContaining({ items: [expect.objectContaining({ cantidad: 1 })] })));
  const ultimo = jest.mocked(guardarBorradorPedido).mock.calls.at(-1)![2];
  await view.unmount();
  jest.mocked(leerBorradorPedido).mockResolvedValueOnce(ultimo);
  const nueva = await render(<CatalogoScreen />);
  expect(await nueva.findByRole('button', { name: 'Quitar Mango' })).toBeTruthy();
});


jest.mock('../../components/ReservationDateTimeField', () => {
  const { TextInput } = jest.requireActual('react-native');
  return {
    ReservationDateTimeField: ({ value, onChange }: { value: string; onChange: (value: string) => void }) => (
      <TextInput accessibilityLabel="Fecha y hora acordadas (AAAA-MM-DDTHH:mm)" value={value} onChangeText={onChange} />
    ),
  };
});
