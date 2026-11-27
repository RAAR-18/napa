import type { PropsWithChildren } from 'react';
import { useFonts } from 'expo-font';
import { ActivityIndicator, StyleSheet, View } from 'react-native';

import { palette } from './theme';

/** Evita medir texto e iconos con una fuente provisional durante el arranque. */
export function AppFonts({ children }: PropsWithChildren) {
  const [loaded, error] = useFonts({
    NapaDisplay: require('../../assets/fonts/Fraunces.ttf'),
    NapaSans: require('../../assets/fonts/DMSans.ttf'),
    NapaIcons: require('../../assets/fonts/Feather.ttf'),
  });

  if (error) throw error;
  if (!loaded) {
    return (
      <View style={styles.loading}>
        <ActivityIndicator accessibilityLabel="Cargando Ñapa" color={palette.coral} />
      </View>
    );
  }
  return children;
}

const styles = StyleSheet.create({
  loading: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: palette.background },
});
