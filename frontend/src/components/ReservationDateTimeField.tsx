import { useState } from 'react';
import DateTimePicker from '@react-native-community/datetimepicker';
import { Platform, Text, View } from 'react-native';

import { fechaDesdeTexto, fechaLocal, type ReservationDateTimeProps } from '../lib/reservation-date';
import { HelperText, TextButton, palette } from './AuthUI';

export function ReservationDateTimeField({ value, onChange, disabled = false }: ReservationDateTimeProps) {
  const [modo, setModo] = useState<'date' | 'time' | null>(null);
  const [minima] = useState(() => new Date());
  const [inicial] = useState(() => new Date(Date.now() + 60 * 60_000));
  const seleccionada = fechaDesdeTexto(value);
  const base = seleccionada ?? inicial;

  function elegir(fecha: Date) {
    const nueva = new Date(base);
    if (modo === 'date') nueva.setFullYear(fecha.getFullYear(), fecha.getMonth(), fecha.getDate());
    else nueva.setHours(fecha.getHours(), fecha.getMinutes());
    onChange(fechaLocal(nueva));
    if (Platform.OS !== 'ios') setModo(null);
  }

  return (
    <View>
      <Text style={{ color: palette.dark, fontWeight: '700', marginBottom: 8 }}>Fecha y hora acordadas</Text>
      <HelperText>{seleccionada
        ? seleccionada.toLocaleString('es-CO', { dateStyle: 'long', timeStyle: 'short' })
        : 'Selecciona el día y la hora de tu reserva.'}</HelperText>
      <TextButton icon="calendar" title="Elegir fecha de reserva" disabled={disabled} onPress={() => setModo('date')} />
      <TextButton icon="clock" title="Elegir hora de reserva" disabled={disabled} onPress={() => setModo('time')} />
      {modo && !disabled ? (
        <DateTimePicker
          testID="selector-reserva"
          value={base}
          mode={modo}
          display={Platform.OS === 'ios' ? 'spinner' : 'default'}
          is24Hour
          minimumDate={modo === 'date' ? minima : undefined}
          onValueChange={(_event, fecha) => elegir(fecha)}
          onDismiss={() => setModo(null)}
        />
      ) : null}
      {modo && Platform.OS === 'ios'
        ? <TextButton icon="check" title="Listo" onPress={() => setModo(null)} /> : null}
    </View>
  );
}
