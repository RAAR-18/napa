import { useState } from 'react';
import { fireEvent, render } from '@testing-library/react-native';

import { TimeField } from './TimeField';

function Formulario({ editable = true }: { editable?: boolean }) {
  const [value, setValue] = useState('');
  return <TimeField label="Horario" value={value} onChangeText={setValue} editable={editable} />;
}

it('formats numeric input and accepts midnight and the last minute of the day', async () => {
  const view = await render(<Formulario />);
  const campo = () => view.getByLabelText('Horario');
  await fireEvent.changeText(campo(), '0830');
  expect(campo().props.value).toBe('08:30');
  await fireEvent.changeText(campo(), '00:00');
  expect(campo().props.value).toBe('00:00');
  await fireEvent.changeText(campo(), '2359');
  expect(campo().props.value).toBe('23:59');
});

it.each(['hola', '24:00', '12:60', '-1:00', '123456'])('rejects invalid input %s without replacing the previous valid time', async (texto) => {
  const view = await render(<Formulario />);
  await fireEvent.changeText(view.getByLabelText('Horario'), '18:30');
  await fireEvent.changeText(view.getByLabelText('Horario'), texto);
  expect(view.getByLabelText('Horario').props.value).toBe('18:30');
  expect(view.getByText('Usa una hora entre 00:00 y 23:59.')).toBeTruthy();
});

it('allows clearing and reports an incomplete time on blur', async () => {
  const view = await render(<Formulario />);
  await fireEvent.changeText(view.getByLabelText('Horario'), '08');
  await fireEvent(view.getByLabelText('Horario'), 'blur', {});
  expect(view.getByText('Usa una hora entre 00:00 y 23:59.')).toBeTruthy();
  await fireEvent.changeText(view.getByLabelText('Horario'), '');
  expect(view.getByLabelText('Horario').props.value).toBe('');
});
