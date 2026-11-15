import { Pressable, StyleSheet, Text, View } from 'react-native';

import { palette } from './AuthUI';

/** Selector de 1 a 5 estrellas; con `onChange` ausente solo muestra el puntaje. */
export default function Estrellas({
  valor,
  onChange,
  etiqueta,
}: {
  valor: number;
  onChange?: (valor: number) => void;
  etiqueta?: string;
}) {
  if (!onChange) {
    return (
      <Text accessibilityLabel={`${valor} de 5 estrellas`} style={styles.static}>
        {'★'.repeat(valor)}
        <Text style={styles.empty}>{'★'.repeat(Math.max(0, 5 - valor))}</Text>
      </Text>
    );
  }
  return (
    <View accessibilityLabel={etiqueta ?? 'Puntaje'} accessibilityRole="radiogroup" style={styles.row}>
      {[1, 2, 3, 4, 5].map((n) => (
        <Pressable
          key={n}
          accessibilityLabel={`${n} ${n === 1 ? 'estrella' : 'estrellas'}`}
          accessibilityRole="radio"
          accessibilityState={{ checked: valor === n }}
          hitSlop={6}
          onPress={() => onChange(n)}
          style={styles.star}
        >
          <Text style={[styles.starText, n <= valor ? styles.filled : styles.empty]}>★</Text>
        </Pressable>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', marginVertical: 6 },
  star: { paddingHorizontal: 4, paddingVertical: 2 },
  starText: { fontSize: 32 },
  filled: { color: '#E8A317' },
  empty: { color: palette.border },
  static: { color: '#E8A317', fontSize: 16 },
});
