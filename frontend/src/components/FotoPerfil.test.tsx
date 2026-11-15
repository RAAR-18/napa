import { fireEvent, render, waitFor } from '@testing-library/react-native';
import * as ImagePicker from 'expo-image-picker';

import FotoPerfil from './FotoPerfil';
import { ApiError } from '../lib/api';
import { subirFotoPerfil } from '../lib/usuarios';

jest.mock('expo-router', () => ({ router: { replace: jest.fn() } }));
jest.mock('expo-image-picker', () => ({
  launchCameraAsync: jest.fn(),
  launchImageLibraryAsync: jest.fn(),
  requestCameraPermissionsAsync: jest.fn(),
  requestMediaLibraryPermissionsAsync: jest.fn(),
}));
jest.mock('../lib/auth', () => ({ clearSession: jest.fn() }));
jest.mock('../lib/usuarios', () => ({
  ...jest.requireActual('../lib/usuarios'),
  subirFotoPerfil: jest.fn(),
}));

function elegida(asset: Partial<ImagePicker.ImagePickerAsset>) {
  return {
    canceled: false,
    assets: [{ uri: 'file:///foto.jpg', width: 400, height: 400, mimeType: 'image/jpeg', fileSize: 120_000, ...asset }],
  } as ImagePicker.ImagePickerResult;
}

beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(ImagePicker.requestMediaLibraryPermissionsAsync).mockResolvedValue({ granted: true } as never);
});

it('crops and compresses before uploading and reports the new photo', async () => {
  jest.mocked(ImagePicker.launchImageLibraryAsync).mockResolvedValue(elegida({ fileName: 'yo.jpg' }));
  jest.mocked(subirFotoPerfil).mockResolvedValue({ fotoUrl: '/media/perfiles/u-1.jpg' });
  const onCambiada = jest.fn();
  const view = await render(<FotoPerfil nombre="Ana" fotoUrl={null} onCambiada={onCambiada} />);

  expect(view.getByText('A')).toBeTruthy();
  await fireEvent.press(view.getByRole('button', { name: 'Elegir foto' }));

  await waitFor(() => expect(onCambiada).toHaveBeenCalledWith('/media/perfiles/u-1.jpg'));
  expect(ImagePicker.launchImageLibraryAsync).toHaveBeenCalledWith(
    expect.objectContaining({ allowsEditing: true, aspect: [1, 1], mediaTypes: ['images'] }),
  );
  expect(subirFotoPerfil).toHaveBeenCalledWith({ uri: 'file:///foto.jpg', name: 'yo.jpg', type: 'image/jpeg' });
});

it('rejects files over 2 MB or with another format without uploading', async () => {
  const view = await render(<FotoPerfil nombre="Ana" fotoUrl={null} onCambiada={jest.fn()} />);

  jest.mocked(ImagePicker.launchImageLibraryAsync).mockResolvedValueOnce(elegida({ fileSize: 3_000_000 }));
  await fireEvent.press(view.getByRole('button', { name: 'Elegir foto' }));
  await waitFor(() => expect(view.getByText('La foto pesa más de 2 MB. Elige otra o recórtala.')).toBeTruthy());

  jest.mocked(ImagePicker.launchImageLibraryAsync).mockResolvedValueOnce(elegida({ mimeType: 'image/gif' }));
  await fireEvent.press(view.getByRole('button', { name: 'Elegir foto' }));
  await waitFor(() => expect(view.getByText('La foto debe ser JPEG, PNG o WebP.')).toBeTruthy());

  expect(subirFotoPerfil).not.toHaveBeenCalled();
});

it('explains a denied permission and a server-side rejection', async () => {
  jest.mocked(ImagePicker.requestMediaLibraryPermissionsAsync).mockResolvedValueOnce({ granted: false } as never);
  const view = await render(<FotoPerfil nombre="Ana" fotoUrl="/media/perfiles/a.png" onCambiada={jest.fn()} />);

  await fireEvent.press(view.getByRole('button', { name: 'Cambiar foto' }));
  await waitFor(() => expect(view.getByText('Necesitamos permiso para ver tus fotos.')).toBeTruthy());

  jest.mocked(ImagePicker.launchImageLibraryAsync).mockResolvedValueOnce(elegida({}));
  jest.mocked(subirFotoPerfil).mockRejectedValueOnce(
    new ApiError('Datos inválidos', 400, 'VALIDATION_ERROR', { fields: { foto: 'La foto debe ser JPEG, PNG o WebP.' } }),
  );
  await fireEvent.press(view.getByRole('button', { name: 'Cambiar foto' }));
  await waitFor(() => expect(view.getByText('La foto debe ser JPEG, PNG o WebP.')).toBeTruthy());
});
