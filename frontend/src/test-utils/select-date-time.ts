import { fireEvent, render } from '@testing-library/react-native';

/** Recorre el selector visual igual que el usuario; los contratos siguen usando fechas locales. */
export async function selectDateTime(view: Pick<Awaited<ReturnType<typeof render>>, 'getByRole'>, label: string, value: string) {
  await fireEvent.press(view.getByRole('button', { name: label }));
  if (value.includes('-')) {
    const now = new Date();
    const target = value.slice(0, 10).split('-').map(Number);
    const months = (target[0] - now.getFullYear()) * 12 + target[1] - 1 - now.getMonth();
    for (let n = 0; n < Math.abs(months); n++) {
      await fireEvent.press(view.getByRole('button', { name: months > 0 ? 'Mes siguiente' : 'Mes anterior' }));
    }
    await fireEvent.press(view.getByRole('button', { name: `Elegir día ${value.slice(0, 10)}` }));
  }
  if (value.includes(':')) {
    const time = value.includes('T') ? value.split('T')[1] : value;
    await fireEvent.press(view.getByRole('button', { name: `Elegir hora ${time.slice(0, 2)}` }));
    await fireEvent.press(view.getByRole('button', { name: `Elegir minuto ${time.slice(3, 5)}` }));
  }
  await fireEvent.press(view.getByRole('button', { name: 'Aplicar selección' }));
}
