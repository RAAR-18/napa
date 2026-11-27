import Constants from 'expo-constants';
import { Platform } from 'react-native';

import { mapasDisponibles, urlRutaGoogleMaps } from './mapas';

jest.mock('expo-constants', () => ({ __esModule: true, default: { expoConfig: { extra: {} } } }));

describe('mapasDisponibles', () => {
  const originalOS = Platform.OS;

  afterEach(() => {
    Object.defineProperty(Platform, 'OS', { configurable: true, value: originalOS });
    Constants.expoConfig!.extra = {};
  });

  it('hides Google Maps on Android when the build has no key', () => {
    Object.defineProperty(Platform, 'OS', { configurable: true, value: 'android' });
    expect(mapasDisponibles()).toBe(false);

    Constants.expoConfig!.extra = { mapasAndroidHabilitados: true };
    expect(mapasDisponibles()).toBe(true);
  });

  it('iOS uses Apple Maps and needs no key', () => {
    Object.defineProperty(Platform, 'OS', { configurable: true, value: 'ios' });
    expect(mapasDisponibles()).toBe(true);
  });
});

describe('rutas externas de Google Maps', () => {
  const destino = { latitud: 11.25, longitud: -74.2 };
  it('usa la ubicación del dispositivo como origen y conserva las coordenadas exactas', () => {
    const url = new URL(urlRutaGoogleMaps(destino));
    expect(url.origin).toBe('https://www.google.com');
    expect(url.searchParams.get('api')).toBe('1');
    expect(url.searchParams.get('destination')).toBe('11.25,-74.2');
    expect(url.searchParams.has('origin')).toBe(false);
    expect(url.searchParams.has('waypoints')).toBe(false);
  });
  it('incluye la recogida como parada antes de la entrega', () => {
    const url = new URL(urlRutaGoogleMaps(destino, { latitud: 11.24, longitud: -74.21 }));
    expect(url.searchParams.get('waypoints')).toBe('11.24,-74.21');
    expect(url.searchParams.get('destination')).toBe('11.25,-74.2');
  });
  it.each([
    { ...destino, aproximado: true },
    { ...destino, latitud: NaN },
    { ...destino, latitud: 91 },
    { ...destino, longitud: -181 },
  ])('rechaza una ubicación aproximada o inválida: %p', (punto) => {
    expect(() => urlRutaGoogleMaps(punto)).toThrow('La ubicación exacta no está disponible.');
  });
});
