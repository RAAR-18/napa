import { Text } from 'react-native';

import { HelperText } from './AuthUI';

type Props = {
  punto: { latitud: number; longitud: number; direccion: string | null };
  titulo: string;
  etiqueta: string;
  posicion?: { lat: number; lng: number } | null;
};

/** react-native-maps no funciona en la web: allí solo queda la dirección en texto. */
export default function MapaPunto(_props: Props) {
  return (
    <HelperText>
      <Text>El mapa solo está disponible en la app del teléfono. Usa la dirección de abajo.</Text>
    </HelperText>
  );
}
