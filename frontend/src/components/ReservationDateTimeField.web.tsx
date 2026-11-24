import { Text, View } from 'react-native';

import { fechaLocal, type ReservationDateTimeProps } from '../lib/reservation-date';
import { palette } from './AuthUI';

export function ReservationDateTimeField({ value, onChange, disabled = false }: ReservationDateTimeProps) {
  return (
    <View style={{ marginBottom: 12 }}>
      <Text style={{ color: palette.dark, fontWeight: '700', marginBottom: 8 }}>Fecha y hora acordadas</Text>
      <input
        aria-label="Fecha y hora de reserva"
        type="datetime-local"
        value={value}
        min={fechaLocal(new Date())}
        step={60}
        disabled={disabled}
        onChange={(event) => onChange(event.currentTarget.value)}
        style={{ boxSizing: 'border-box', width: '100%', minHeight: 48, padding: 12,
          border: `1px solid ${palette.border}`, borderRadius: 12, background: '#FFFFFF',
          color: palette.dark, fontSize: 16, fontFamily: 'inherit' }}
      />
    </View>
  );
}
