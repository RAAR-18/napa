import { fireEvent, render } from '@testing-library/react-native';
import { router } from 'expo-router';

import { ProductoCard } from './ProductosUI';
import type { Producto } from '../lib/productos';

jest.mock('expo-router', () => ({ router: { push: jest.fn() } }));

const producto = {
  id: 'p-1',
  nombre: 'Mango',
  precio: 3000,
  unidad: 'UNIDAD',
  descripcion: null,
  cantidadDisponible: 10,
  soloReserva: false,
} as unknown as Producto;

beforeEach(() => jest.clearAllMocks());

it('shows the product average and opens its reviews', async () => {
  const view = await render(<ProductoCard producto={producto} resumen={{ promedio: 4.3, total: 12 }} />);

  expect(view.getByLabelText(/Calificación de Mango: 4,3 de 5 · 12 calificaciones/)).toBeTruthy();
  await fireEvent.press(view.getByRole('button', { name: 'Ver opiniones de Mango' }));

  expect(router.push).toHaveBeenCalledWith({
    pathname: '/calificaciones',
    params: { tipoEntidad: 'PRODUCTO', entidadId: 'p-1', titulo: 'Mango' },
  });
});

it('says there are no ratings yet and hides the link', async () => {
  const view = await render(<ProductoCard producto={producto} resumen={{ promedio: null, total: 0 }} />);

  expect(view.getByText('Aún sin calificaciones')).toBeTruthy();
  expect(view.queryByRole('button', { name: 'Ver opiniones de Mango' })).toBeNull();
});

it('renders without ratings while they load', async () => {
  const view = await render(<ProductoCard producto={producto} />);

  expect(view.queryByText(/calificaciones/)).toBeNull();
});
