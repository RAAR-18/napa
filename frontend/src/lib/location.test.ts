import * as Location from 'expo-location';

import {
  LocationPermissionDeniedError,
  LocationUnavailableError,
  requestDeviceLocation,
} from './location';

jest.mock('expo-location', () => ({
  Accuracy: { Balanced: 3 },
  requestForegroundPermissionsAsync: jest.fn(),
  getCurrentPositionAsync: jest.fn(),
}));

describe('requestDeviceLocation', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns device coordinates when foreground permission is granted', async () => {
    jest
      .mocked(Location.requestForegroundPermissionsAsync)
      .mockResolvedValue({
        status: 'granted',
      } as Location.LocationPermissionResponse);
    jest.mocked(Location.getCurrentPositionAsync).mockResolvedValue({
      coords: { latitude: 4.711, longitude: -74.0721 },
    } as Location.LocationObject);

    const coords = await requestDeviceLocation();

    expect(coords).toEqual({
      latitude: 4.711,
      longitude: -74.0721,
    });
    expect(Location.requestForegroundPermissionsAsync).toHaveBeenCalledTimes(1);
    expect(Location.getCurrentPositionAsync).toHaveBeenCalledWith({
      accuracy: Location.Accuracy.Balanced,
    });
  });

  it('throws LocationPermissionDeniedError when permission is denied', async () => {
    jest
      .mocked(Location.requestForegroundPermissionsAsync)
      .mockResolvedValue({
        status: 'denied',
      } as Location.LocationPermissionResponse);

    await expect(requestDeviceLocation()).rejects.toThrow(
      LocationPermissionDeniedError,
    );
    expect(Location.getCurrentPositionAsync).not.toHaveBeenCalled();
  });

  it('throws LocationUnavailableError when position retrieval fails', async () => {
    jest
      .mocked(Location.requestForegroundPermissionsAsync)
      .mockResolvedValue({
        status: 'granted',
      } as Location.LocationPermissionResponse);
    jest
      .mocked(Location.getCurrentPositionAsync)
      .mockRejectedValue(new Error('GPS service timeout'));

    await expect(requestDeviceLocation()).rejects.toThrow(
      LocationUnavailableError,
    );
  });
});
