import { useEffect, useState } from 'react';
import { Animated, Platform, Pressable, StyleSheet, Text, View } from 'react-native';

import { Icon } from './NapaUI';
import { fonts, palette } from './theme';

export type ToastTone = 'error' | 'success';

const DURACION_MS = 6000;
// En la web la notificación queda fija arriba de la ventana aunque la página tenga scroll.
const POSICION = (Platform.OS === 'web' ? 'fixed' : 'absolute') as 'absolute';
const NATIVE_DRIVER = Platform.OS !== 'web';

/**
 * Notificación al estilo iPhone: baja desde arriba, dice qué pasó y se oculta sola
 * a los pocos segundos (o al tocarla).
 */
export function Toast({ message, tone = 'error' }: { message: string | null; tone?: ToastTone }) {
  // Mensaje que la persona cerró (o que ya expiró); uno nuevo vuelve a mostrarse.
  const [cerrado, setCerrado] = useState<string | null>(null);
  const [desplazamiento] = useState(() => new Animated.Value(-140));
  const visible = message && message !== cerrado ? message : null;

  useEffect(() => {
    if (!message) {
      return undefined;
    }
    desplazamiento.setValue(-140);
    Animated.spring(desplazamiento, {
      toValue: 0,
      useNativeDriver: NATIVE_DRIVER,
      friction: 8,
      tension: 70,
    }).start();
    const timer = setTimeout(() => {
      Animated.timing(desplazamiento, {
        toValue: -140,
        duration: 220,
        useNativeDriver: NATIVE_DRIVER,
      }).start(() => setCerrado(message));
    }, DURACION_MS);
    return () => clearTimeout(timer);
  }, [message, desplazamiento]);

  function cerrar() {
    Animated.timing(desplazamiento, {
      toValue: -140,
      duration: 220,
      useNativeDriver: NATIVE_DRIVER,
    }).start(() => setCerrado(message));
  }

  if (!visible) {
    return null;
  }
  const esError = tone === 'error';
  return (
    <View pointerEvents="box-none" style={styles.capa}>
      <Animated.View style={[styles.contenedor, { transform: [{ translateY: desplazamiento }] }]}>
        <Pressable
          accessibilityHint="Toca para cerrar"
          accessibilityLiveRegion="assertive"
          accessibilityRole="alert"
          onPress={cerrar}
          style={styles.tarjeta}
        >
          <View style={[styles.icono, esError ? styles.iconoError : styles.iconoExito]}>
            <Icon
              color="#FFFFFF"
              name={esError ? 'alert-circle' : 'check-circle'}
              size={20}
            />
          </View>
          <View style={styles.cuerpo}>
            <View style={styles.cabecera}>
              <Text style={styles.app}>ÑAPA</Text>
              <Text style={styles.hora}>ahora</Text>
            </View>
            <Text style={styles.titulo}>{esError ? 'No se pudo completar' : 'Listo'}</Text>
            <Text style={styles.mensaje}>{visible}</Text>
          </View>
        </Pressable>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  capa: {
    alignItems: 'center',
    left: 0,
    paddingHorizontal: 12,
    position: POSICION,
    right: 0,
    top: 10,
    zIndex: 1000,
  },
  contenedor: { maxWidth: 440, width: '100%' },
  tarjeta: {
    alignItems: 'flex-start',
    backgroundColor: 'rgba(255, 252, 247, 0.97)',
    borderColor: 'rgba(52, 32, 54, 0.08)',
    borderRadius: 22,
    borderWidth: 1,
    elevation: 12,
    flexDirection: 'row',
    gap: 12,
    paddingHorizontal: 14,
    paddingVertical: 12,
    shadowColor: '#000000',
    shadowOffset: { width: 0, height: 10 },
    shadowOpacity: 0.18,
    shadowRadius: 24,
  },
  icono: {
    alignItems: 'center',
    borderRadius: 10,
    height: 38,
    justifyContent: 'center',
    width: 38,
  },
  iconoError: { backgroundColor: palette.error },
  iconoExito: { backgroundColor: '#3F8F5A' },
  cuerpo: { flex: 1, minWidth: 0 },
  cabecera: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 2 },
  app: {
    color: palette.muted,
    fontFamily: fonts.sans,
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 0.6,
  },
  hora: { color: palette.muted, fontFamily: fonts.sans, fontSize: 11 },
  titulo: { color: palette.dark, fontFamily: fonts.sans, fontSize: 15, fontWeight: '700' },
  mensaje: { color: palette.dark, fontFamily: fonts.sans, fontSize: 14, lineHeight: 19, marginTop: 1 },
});
