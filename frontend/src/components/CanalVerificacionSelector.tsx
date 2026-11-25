import { StyleSheet, Text, View } from 'react-native';

import { OptionButton } from './AuthUI';
import type { VerificationChannel } from '../lib/auth';

const opciones: { value: VerificationChannel; label: string }[] = [
  { value: 'SMS', label: 'Por SMS' },
  { value: 'CORREO', label: 'Por correo' },
];

/** Elige por dónde llega el código de verificación: SMS al celular o correo. */
export function CanalVerificacionSelector({
  label = '¿Cómo quieres recibir el código?',
  value,
  onChange,
  disabled = false,
}: {
  label?: string;
  disabled?: boolean;
  value: VerificationChannel;
  onChange: (canal: VerificationChannel) => void;
}) {
  return (
    <View style={styles.container}>
      <Text style={styles.label}>{label}</Text>
      <View accessibilityRole="radiogroup" style={styles.options}>
        {opciones.map((opcion) => (
          <OptionButton
            key={opcion.value}
            label={opcion.label}
            onPress={() => { if (!disabled) onChange(opcion.value); }}
            selected={value === opcion.value}
          />
        ))}
      </View>
    </View>
  );
}

export function describirCanal(canal: VerificationChannel): string {
  return canal === 'CORREO' ? 'tu correo' : 'SMS';
}

const styles = StyleSheet.create({
  container: { gap: 8, marginBottom: 12 },
  label: { fontSize: 15, fontWeight: '600' },
  options: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
});
