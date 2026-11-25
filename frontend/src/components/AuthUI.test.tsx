import { fireEvent, render } from '@testing-library/react-native';
import { View } from 'react-native';

import { Field } from './AuthUI';

it('shows and hides a password without changing its value', async () => {
  const onChangeText = jest.fn();
  const view = await render(
    <Field label="Contraseña" value="ClaveDePrueba!" onChangeText={onChangeText} secureTextEntry showPasswordToggle />,
  );

  expect(view.getByLabelText('Contraseña').props.secureTextEntry).toBe(true);
  await fireEvent.press(view.getByRole('button', { name: 'Mostrar contraseña' }));
  expect(view.getByLabelText('Contraseña').props.secureTextEntry).toBe(false);
  expect(view.getByLabelText('Contraseña').props.value).toBe('ClaveDePrueba!');
  expect(onChangeText).not.toHaveBeenCalled();

  await fireEvent.press(view.getByRole('button', { name: 'Ocultar contraseña' }));
  expect(view.getByLabelText('Contraseña').props.secureTextEntry).toBe(true);
});

it('keeps confirmation independent and leaves other sensitive fields masked', async () => {
  const view = await render(
    <View>
      <Field label="Contraseña" value="ClaveDePrueba!" secureTextEntry showPasswordToggle />
      <Field label="Confirma tu contraseña" value="ClaveDePrueba!" secureTextEntry showPasswordToggle />
      <Field label="Cuenta bancaria" value="1234567890" secureTextEntry />
    </View>,
  );

  expect(view.getAllByRole('button', { name: 'Mostrar contraseña' })).toHaveLength(2);
  await fireEvent.press(view.getAllByRole('button', { name: 'Mostrar contraseña' })[0]);
  expect(view.getByLabelText('Contraseña').props.secureTextEntry).toBe(false);
  expect(view.getByLabelText('Confirma tu contraseña').props.secureTextEntry).toBe(true);
  expect(view.getByLabelText('Cuenta bancaria').props.secureTextEntry).toBe(true);
});
