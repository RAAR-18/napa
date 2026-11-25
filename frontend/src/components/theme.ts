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
  cliente: require('../../assets/illustrations/cliente.webp'),
  vendedor: require('../../assets/illustrations/vendedor.webp'),
  domiciliario: require('../../assets/illustrations/domiciliario.webp'),
  administrador: require('../../assets/illustrations/administrador.webp'),
  frutas: require('../../assets/illustrations/frutas.webp'),
  comida: require('../../assets/illustrations/comida.webp'),
  pescado: require('../../assets/illustrations/pescado.webp'),
  paquete: require('../../assets/illustrations/paquete.webp'),
  canasta: require('../../assets/illustrations/canasta.webp'),
};

export function categoryArt(category: string) {
  if (category === 'PESCADO_MARISCOS') return art.pescado;
  if (category === 'ALIMENTOS_PREPARADOS' || category === 'BEBIDAS' || category === 'SNACKS')
    return art.comida;
  return art.frutas;
}
