export type ReservationDateTimeProps = {
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
};

export function fechaLocal(value: Date): string {
  const dos = (n: number) => String(n).padStart(2, '0');
  return `${value.getFullYear()}-${dos(value.getMonth() + 1)}-${dos(value.getDate())}T${dos(value.getHours())}:${dos(value.getMinutes())}`;
}

export function fechaDesdeTexto(value: string): Date | null {
  const partes = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(value);
  if (!partes) return null;
  const [, y, m, d, h, min] = partes.map(Number);
  const fecha = new Date(y, m - 1, d, h, min);
  return fecha.getFullYear() === y && fecha.getMonth() === m - 1 && fecha.getDate() === d
    && fecha.getHours() === h && fecha.getMinutes() === min ? fecha : null;
}
