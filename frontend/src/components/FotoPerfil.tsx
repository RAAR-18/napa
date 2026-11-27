import { fonts } from './theme';
import { useState } from 'react';
import * as ImagePicker from 'expo-image-picker';
import { Image, Platform, StyleSheet, Text, View } from 'react-native';

import { ErrorBanner, HelperText, TextButton, palette } from './AuthUI';
import { ApiError } from '../lib/api';
import { manejarError, mensajeDeCampo } from '../lib/errores';
import { FOTO_MAXIMA_BYTES, TIPOS_FOTO, subirFotoPerfil, urlFoto } from '../lib/usuarios';

/** Recorte cuadrado y compresión en el teléfono antes de subir (RNF31: gama baja y datos móviles). */
const OPCIONES: ImagePicker.ImagePickerOptions = {
  mediaTypes: ['images'],
  allowsEditing: true,
  aspect: [1, 1],
  quality: 0.6,
};

const extensiones: Record<string, string> = { 'image/jpeg': 'jpg', 'image/png': 'png', 'image/webp': 'webp' };

/** RF-08: foto de perfil desde la galería o la cámara (JPEG, PNG o WebP de hasta 2 MB). */
export default function FotoPerfil({
  nombre,
  fotoUrl,
  onCambiada,
}: {
  nombre: string;
  fotoUrl: string | null;
  onCambiada: (fotoUrl: string) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const foto = urlFoto(fotoUrl);

  async function elegir(origen: 'galeria' | 'camara') {
    setError(null);
    try {
      const permiso =
        origen === 'camara'
          ? await ImagePicker.requestCameraPermissionsAsync()
          : await ImagePicker.requestMediaLibraryPermissionsAsync();
      if (!permiso.granted) {
        setError(
          origen === 'camara'
            ? 'Necesitamos permiso para usar la cámara.'
            : 'Necesitamos permiso para ver tus fotos.',
        );
        return;
      }
      const resultado =
        origen === 'camara'
          ? await ImagePicker.launchCameraAsync(OPCIONES)
          : await ImagePicker.launchImageLibraryAsync(OPCIONES);
      if (resultado.canceled || resultado.assets.length === 0) return;
      const asset = resultado.assets[0];
      const tipo = asset.mimeType ?? 'image/jpeg';
      if (!TIPOS_FOTO.includes(tipo)) {
        setError('La foto debe ser JPEG, PNG o WebP.');
        return;
      }
      if (asset.fileSize !== undefined && asset.fileSize > FOTO_MAXIMA_BYTES) {
        setError('La foto pesa más de 2 MB. Elige otra o recórtala.');
        return;
      }
      setBusy(true);
      const subida = await subirFotoPerfil({
        uri: asset.uri,
        name: asset.fileName ?? `perfil.${extensiones[tipo] ?? 'jpg'}`,
        type: tipo,
      });
      onCambiada(subida.fotoUrl);
    } catch (cause) {
      setError(
        cause instanceof ApiError && cause.code === 'EXTERNAL_SERVICE_UNAVAILABLE'
          ? 'No pudimos guardar la foto; intenta de nuevo.'
          : (mensajeDeCampo(cause) ?? (await manejarError(cause, 'No se pudo subir la foto.'))),
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <View style={styles.box}>
      {foto ? (
        <Image accessibilityLabel="Foto de perfil" source={{ uri: foto }} style={styles.photo} />
      ) : (
        <View accessibilityLabel="Sin foto de perfil" style={[styles.photo, styles.initials]}>
          <Text style={styles.initialsText}>{nombre.charAt(0).toUpperCase()}</Text>
        </View>
      )}
      {busy ? <HelperText>Subiendo foto…</HelperText> : null}
      <View style={styles.row}>
        <TextButton
          title={foto ? 'Cambiar foto' : 'Elegir foto'}
          variant="soft"
          icon="camera"
          disabled={busy}
          onPress={() => void elegir('galeria')}
        />
        {Platform.OS !== 'web' ? (
          <TextButton title="Tomar foto" variant="soft" icon="camera" disabled={busy} onPress={() => void elegir('camara')} />
        ) : null}
      </View>
      <ErrorBanner message={error} />
    </View>
  );
}

const styles = StyleSheet.create({
  box: { alignItems: 'center', marginBottom: 8 },
  photo: { borderRadius: 48, height: 96, width: 96 },
  initials: { alignItems: 'center', backgroundColor: palette.green, justifyContent: 'center' },
  initialsText: { color: palette.white, fontFamily: fonts.sans, fontSize: 36, fontWeight: '800' },
  row: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'center', gap: 16, marginTop: 12 },
});
