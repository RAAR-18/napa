import { fireEvent, render } from '@testing-library/react-native';

import { DateTimeField } from './DateTimeField';

it('permite elegir un día de un mes bisiesto y conserva la hora local hasta aplicar', async () => {
  const change = jest.fn();
  const view = await render(<DateTimeField label="Reserva" mode="datetime" value="2028-02-28T08:15" onChangeText={change} />);
  await fireEvent.press(view.getByRole('button', { name: 'Reserva' }));
  expect(view.queryByRole('button', { name: 'Elegir día 2028-02-30' })).toBeNull();
  await fireEvent.press(view.getByRole('button', { name: 'Elegir día 2028-02-29' }));
  await fireEvent.press(view.getByRole('button', { name: 'Elegir hora 23' }));
  await fireEvent.press(view.getByRole('button', { name: 'Elegir minuto 45' }));
  expect(change).not.toHaveBeenCalled();
  await fireEvent.press(view.getByRole('button', { name: 'Aplicar selección' }));
  expect(change).toHaveBeenCalledWith('2028-02-29T23:45');
});

it('navega entre meses sin cambiar la fecha al cancelar', async () => {
  const change = jest.fn();
  const view = await render(<DateTimeField label="Desde" mode="date" value="2027-01-31" onChangeText={change} />);
  await fireEvent.press(view.getByRole('button', { name: 'Desde' }));
  await fireEvent.press(view.getByRole('button', { name: 'Mes siguiente' }));
  expect(view.queryByRole('button', { name: 'Elegir día 2027-02-29' })).toBeNull();
  await fireEvent.press(view.getByRole('button', { name: 'Elegir día 2027-02-28' }));
  await fireEvent.press(view.getByRole('button', { name: 'Cancelar selección' }));
  expect(change).not.toHaveBeenCalled();
  await fireEvent.press(view.getByRole('button', { name: 'Desde' }));
  expect(view.getByRole('button', { name: 'Elegir día 2027-01-31' }).props.accessibilityState.selected).toBe(true);
  await fireEvent.press(view.getByRole('button', { name: 'Quitar fecha' }));
  expect(change).toHaveBeenCalledWith('');
});

it('elige medianoche en formato de 24 horas sin calendario', async () => {
  const change = jest.fn();
  const view = await render(<DateTimeField label="Cierre" mode="time" value="18:30" onChangeText={change} />);
  await fireEvent.press(view.getByRole('button', { name: 'Cierre' }));
  expect(view.queryByRole('button', { name: 'Mes siguiente' })).toBeNull();
  await fireEvent.press(view.getByRole('button', { name: 'Elegir hora 00' }));
  await fireEvent.press(view.getByRole('button', { name: 'Elegir minuto 00' }));
  await fireEvent.press(view.getByRole('button', { name: 'Aplicar selección' }));
  expect(change).toHaveBeenCalledWith('00:00');
});

it('no abre un campo bloqueado', async () => {
  const view = await render(<DateTimeField label="Horario" mode="time" value="08:00" onChangeText={jest.fn()} editable={false} />);
  await fireEvent.press(view.getByRole('button', { name: 'Horario' }));
  expect(view.queryByRole('button', { name: 'Aplicar selección' })).toBeNull();
});
