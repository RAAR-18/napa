import * as Location from 'expo-location';

export interface DeviceCoordinates {
  latitude: number;
  longitude: number;
}

export class LocationPermissionDeniedError extends Error {
  constructor(
    message = 'No se concedió permiso de ubicación. Puedes escribir las coordenadas manualmente.',
  ) {
    super(message);
    this.name = 'LocationPermissionDeniedError';
  }
}

export class LocationUnavailableError extends Error {
  constructor(
    message = 'No se pudo obtener la ubicación del dispositivo.',
  ) {
    super(message);
    this.name = 'LocationUnavailableError';
  }
}

export async function requestDeviceLocation(): Promise<DeviceCoordinates> {
  const permission = await Location.requestForegroundPermissionsAsync();
  if (permission.status !== 'granted') {
    throw new LocationPermissionDeniedError();
  }

  try {
    const location = await Location.getCurrentPositionAsync({
      accuracy: Location.Accuracy.Balanced,
    });

    return {
      latitude: location.coords.latitude,
      longitude: location.coords.longitude,
    };
  } catch (cause) {
    const detail = cause instanceof Error ? `: ${cause.message}` : '.';
    throw new LocationUnavailableError(
      `No se pudo obtener la ubicación del dispositivo${detail}`,
    );
  }
}
