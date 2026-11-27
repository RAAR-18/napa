import { fireEvent, render } from '@testing-library/react-native';
import { Linking } from 'react-native';

import { GoogleMapsButton } from './GoogleMapsButton';

it('muestra un aviso si el sistema no puede abrir Google Maps', async () => {
  const abrir = jest.spyOn(Linking, 'openURL').mockRejectedValue(new Error('No handler'));
  try {
    const view = await render(<GoogleMapsButton title="Ver ruta" destino={{ latitud: 11.25, longitud: -74.2 }} />);
    await fireEvent.press(view.getByRole('button', { name: 'Ver ruta' }));
    expect(await view.findByText('No se pudo abrir Google Maps. Inténtalo de nuevo.')).toBeTruthy();
  } finally { abrir.mockRestore(); }
});
