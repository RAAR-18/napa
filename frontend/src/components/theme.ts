export const palette = {
  green: '#E95F3D',
  coral: '#E95F3D',
  coralDark: '#C84529',
  plum: '#342036',
  dark: '#342036',
  muted: '#796C72',
  border: '#DBC9BD',
  background: '#FAF6EF',
  white: '#FFFCF7',
  peach: '#FFE0C4',
  sage: '#E8EFCF',
  lavender: '#EDE3EF',
  error: '#AC352C',
  errorBackground: '#FFF0E9',
};

export const fonts = { display: 'NapaDisplay', sans: 'NapaSans', icons: 'NapaIcons' };
export const DESKTOP_BREAKPOINT = 1024;

export const art = {
  cliente: require('../../assets/illustrations/cliente.png'),
  vendedor: require('../../assets/illustrations/vendedor.png'),
  domiciliario: require('../../assets/illustrations/domiciliario.png'),
  administrador: require('../../assets/illustrations/administrador.png'),
  frutas: require('../../assets/illustrations/frutas.png'),
  comida: require('../../assets/illustrations/comida.png'),
  pescado: require('../../assets/illustrations/pescado.png'),
  paquete: require('../../assets/illustrations/paquete.png'),
  canasta: require('../../assets/illustrations/canasta.png'),
};

export function categoryArt(category: string) {
  if (category === 'PESCADO_MARISCOS') return art.pescado;
  if (category === 'ALIMENTOS_PREPARADOS' || category === 'BEBIDAS' || category === 'SNACKS')
    return art.comida;
  return art.frutas;
}
