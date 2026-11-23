import { useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';

import { fonts, palette } from './theme';

export type Pais = { bandera: string; nombre: string; prefijo: string };

export const PAISES: Pais[] = [
  { bandera: '🇨🇴', nombre: 'Colombia', prefijo: '+57' },
  { bandera: '🇻🇪', nombre: 'Venezuela', prefijo: '+58' },
  { bandera: '🇪🇨', nombre: 'Ecuador', prefijo: '+593' },
  { bandera: '🇵🇪', nombre: 'Perú', prefijo: '+51' },
  { bandera: '🇵🇦', nombre: 'Panamá', prefijo: '+507' },
  { bandera: '🇲🇽', nombre: 'México', prefijo: '+52' },
  { bandera: '🇦🇷', nombre: 'Argentina', prefijo: '+54' },
  { bandera: '🇨🇱', nombre: 'Chile', prefijo: '+56' },
  { bandera: '🇺🇸', nombre: 'Estados Unidos', prefijo: '+1' },
  { bandera: '🇪🇸', nombre: 'España', prefijo: '+34' },
];

/** Une el prefijo del país con el número local, sin espacios ni separadores. */
export function celularCompleto(pais: Pais, numero: string): string {
  const digitos = numero.replace(/[^0-9]/g, '');
  return digitos ? `${pais.prefijo}${digitos}` : '';
}

/**
 * Celular con selector de país (bandera y prefijo) a la izquierda.
 * Devuelve el número en formato internacional, por ejemplo +573001234567.
 */
export function CelularField({
  label,
  onChange,
  editable = true,
}: {
  label: string;
  onChange: (celular: string) => void;
  editable?: boolean;
}) {
  const [pais, setPais] = useState<Pais>(PAISES[0]);
  const [numero, setNumero] = useState('');
  const [abierto, setAbierto] = useState(false);
  const [focused, setFocused] = useState(false);

  function elegir(nuevo: Pais) {
    setPais(nuevo);
    setAbierto(false);
    onChange(celularCompleto(nuevo, numero));
  }

  return (
    <View style={styles.field}>
      <Text style={styles.label}>{label}</Text>
      <View style={[styles.row, focused && styles.rowFocused]}>
        <Pressable
          accessibilityLabel={`País: ${pais.nombre} ${pais.prefijo}`}
          accessibilityRole="button"
          disabled={!editable}
          onPress={() => setAbierto((actual) => !actual)}
          style={styles.pais}
        >
          <Text style={styles.paisTexto}>{`${pais.bandera} ${pais.prefijo} ▾`}</Text>
        </Pressable>
        <TextInput
          accessibilityLabel={label}
          editable={editable}
          keyboardType="phone-pad"
          maxLength={15}
          onBlur={() => setFocused(false)}
          onChangeText={(texto) => {
            const limpio = texto.replace(/[^0-9]/g, '');
            setNumero(limpio);
            onChange(celularCompleto(pais, limpio));
          }}
          onFocus={() => setFocused(true)}
          placeholder="300 123 4567"
          placeholderTextColor="#A29289"
          style={styles.input}
          value={numero}
        />
      </View>
      {abierto ? (
        <View accessibilityRole="menu" style={styles.lista}>
          {PAISES.map((opcion) => (
            <Pressable
              accessibilityLabel={`${opcion.nombre} ${opcion.prefijo}`}
              accessibilityRole="menuitem"
              key={opcion.prefijo + opcion.nombre}
              onPress={() => elegir(opcion)}
              style={[styles.opcion, opcion === pais && styles.opcionActiva]}
            >
              <Text style={styles.opcionTexto}>
                {`${opcion.bandera}  ${opcion.nombre}  ${opcion.prefijo}`}
              </Text>
            </Pressable>
          ))}
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  field: { marginBottom: 18, flexShrink: 1 },
  label: {
    color: palette.dark,
    fontFamily: fonts.sans,
    fontSize: 13,
    fontWeight: '700',
    marginBottom: 8,
  },
  row: {
    backgroundColor: palette.white,
    borderColor: palette.border,
    borderRadius: 12,
    borderWidth: 1,
    flexDirection: 'row',
    minHeight: 50,
    overflow: 'hidden',
  },
  rowFocused: { borderColor: palette.coral, backgroundColor: '#FFFFFF' },
  pais: {
    alignItems: 'center',
    borderColor: palette.border,
    borderRightWidth: 1,
    justifyContent: 'center',
    paddingHorizontal: 12,
  },
  paisTexto: { color: palette.dark, fontFamily: fonts.sans, fontSize: 15, fontWeight: '600' },
  input: {
    color: palette.dark,
    flex: 1,
    fontFamily: fonts.sans,
    fontSize: 15,
    minWidth: 0,
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  lista: {
    backgroundColor: '#FFFFFF',
    borderColor: palette.border,
    borderRadius: 12,
    borderWidth: 1,
    marginTop: 6,
    paddingVertical: 4,
  },
  opcion: { paddingHorizontal: 14, paddingVertical: 10 },
  opcionActiva: { backgroundColor: '#FBEDE6' },
  opcionTexto: { color: palette.dark, fontFamily: fonts.sans, fontSize: 15 },
});
