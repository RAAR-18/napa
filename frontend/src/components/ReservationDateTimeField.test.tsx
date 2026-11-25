import { useState } from 'react';
import { fireEvent, render } from '@testing-library/react-native';
import { Text } from 'react-native';

import { ReservationDateTimeField } from './ReservationDateTimeField';
import { fechaDesdeTexto, fechaLocal } from '../lib/reservation-date';

jest.mock('@react-native-community/datetimepicker', () => ({
  __esModule: true,
  default: jest.requireActual('react-native').View,
}));

function Formulario() {
  const [value, setValue] = useState('');
  return <><ReservationDateTimeField value={value} onChange={setValue} /><Text>{`valor:${value}`}</Text></>;
}

it('selects a local date and time without typing the ISO representation', async () => {
  const view = await render(<Formulario />);
  await fireEvent.press(view.getByRole('button', { name: 'Elegir fecha de reserva' }));
  await fireEvent(view.getByTestId('selector-reserva'), 'valueChange', {}, new Date(2027, 0, 5));
  await fireEvent.press(view.getByRole('button', { name: 'Elegir hora de reserva' }));
  await fireEvent(view.getByTestId('selector-reserva'), 'valueChange', {}, new Date(2027, 0, 5, 15, 30));
  expect(view.getByText('valor:2027-01-05T15:30')).toBeTruthy();
});

it('does not invent a reservation when the user cancels the picker', async () => {
  const view = await render(<Formulario />);
  await fireEvent.press(view.getByRole('button', { name: 'Elegir fecha de reserva' }));
  await fireEvent(view.getByTestId('selector-reserva'), 'dismiss');
  expect(view.getByText('valor:')).toBeTruthy();
});

it('keeps local time and rejects nonexistent dates', () => {
  expect(fechaLocal(new Date(2027, 0, 5, 15, 30))).toBe('2027-01-05T15:30');
  expect(fechaDesdeTexto('2027-01-05T15:30')?.getHours()).toBe(15);
  expect(fechaDesdeTexto('2027-02-31T15:30')).toBeNull();
});
