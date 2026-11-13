import { Text } from 'react-native';

import type { RutaDomicilio } from '../lib/domicilios';
import { HelperText } from './AuthUI';

type Props = {
  ruta: RutaDomicilio;
  posicion: { lat: number; lng: number } | null;
};

/** react-native-maps no funciona en la web: allí solo quedan las direcciones en texto. */
export default function MapaRutaDomicilio(_props: Props) {
  return (
    <HelperText>
      <Text>El mapa solo está disponible en la app del teléfono. Usa las direcciones de abajo.</Text>
    </HelperText>
  );
}
