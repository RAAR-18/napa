import { useState } from 'react';

import { Field, HelperText } from './AuthUI';

type Props = {
  label: string;
  value: string;
  onChangeText: (value: string) => void;
  editable?: boolean;
  placeholder?: string;
};

const MENSAJE = 'Usa una hora entre 00:00 y 23:59.';

/** Entrada de 24 horas: solo dígitos y separador automático, sin depender del teclado. */
export function TimeField({ label, value, onChangeText, editable = true, placeholder = '08:00' }: Props) {
  const [error, setError] = useState<string | null>(null);

  function cambiar(texto: string) {
    if (!editable) return;
    if (!/^[\d:]*$/.test(texto)) {
      setError(MENSAJE);
      return;
    }
    const digitos = texto.replace(/:/g, '');
    if (digitos.length > 4
      || (digitos.length >= 2 && Number(digitos.slice(0, 2)) > 23)
      || (digitos.length >= 3 && Number(digitos[2]) > 5)) {
      setError(MENSAJE);
      return;
    }
    const normalizado = digitos.length > 2
      ? `${digitos.slice(0, 2)}:${digitos.slice(2)}`
      : digitos;
    setError(null);
    onChangeText(normalizado);
  }

  return (
    <>
      <Field
        label={label}
        value={value}
        onChangeText={cambiar}
        editable={editable}
        keyboardType="number-pad"
        placeholder={placeholder}
        onBlur={() => setError(value && !/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(value) ? MENSAJE : null)}
      />
      {error ? <HelperText>{error}</HelperText> : null}
    </>
  );
}
