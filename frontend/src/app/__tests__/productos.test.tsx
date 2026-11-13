import { act, fireEvent, render, waitFor } from '@testing-library/react-native';
import { router } from 'expo-router';
import { AppState, type AppStateStatus } from 'react-native';

import CatalogoScreen from '../catalogo';
import MisProductosScreen from '../mis-productos';
import NuevoProductoScreen from '../nuevo-producto';
import EditarProductoScreen from '../editar-producto';
import { ApiError } from '../../lib/api';
import { clearSession, getValidSession } from '../../lib/auth';
import { createProducto, deleteProducto, editProducto, getMiProducto, listCatalogo, listMisProductos, updateInventario, type Producto, type ProductosPage } from '../../lib/productos';

jest.mock('expo-router', () => ({
  router: { push: jest.fn(), replace: jest.fn() },
  useLocalSearchParams: () => ({
    emprendimientoId: 'business-id',
    tipoVendedor: 'AMBULANTE',
    productoId: 'product-id',
  }),
  useFocusEffect: (effect: () => void | (() => void)) => {
    const React = jest.requireActual<typeof import('react')>('react');
    React.useEffect(effect, [effect]);
  },
}));
jest.mock('../../lib/auth', () => ({ getValidSession: jest.fn(), clearSession: jest.fn() }));
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
  }));
  expect(router.replace).toHaveBeenCalledWith('/mis-productos');
}, 15000);

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
