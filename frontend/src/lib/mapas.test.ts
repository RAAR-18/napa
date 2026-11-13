import Constants from 'expo-constants';
import { Platform } from 'react-native';

import { mapasDisponibles } from './mapas';

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
