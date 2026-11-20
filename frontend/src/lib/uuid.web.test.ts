import { getRandomBytes } from 'expo-crypto';

import { randomUUID } from './uuid.web';

jest.mock('expo-crypto', () => ({ getRandomBytes: jest.fn() }));

describe('UUID web para pruebas en la red local', () => {
  beforeEach(() => jest.resetAllMocks());

  it.each<[number, string]>([
    [0x00, '00000000-0000-4000-8000-000000000000'],
    [0xff, 'ffffffff-ffff-4fff-bfff-ffffffffffff'],
  ])('genera un UUID v4 valido con los bytes del proveedor (%i)', (byte, expected) => {
    jest.mocked(getRandomBytes).mockReturnValue(new Uint8Array(16).fill(byte));

    expect(randomUUID()).toBe(expected);
    expect(getRandomBytes).toHaveBeenCalledWith(16);
  });

  it('propaga la falta de aleatoriedad segura', () => {
    jest.mocked(getRandomBytes).mockImplementation(() => {
      throw new Error('No secure entropy');
    });

    expect(() => randomUUID()).toThrow('No secure entropy');
  });
});
