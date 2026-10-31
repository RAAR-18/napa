import React, { useState } from 'react';
import {
  ActivityIndicator,
  Image,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import { useAuth } from '../context/AuthContext';
import { createProducto } from '../services/api';

interface CreateProductScreenProps {
  onGoBack: () => void;
  onCreated: () => void;
}

export const CreateProductScreen: React.FC<CreateProductScreenProps> = ({ onGoBack, onCreated }) => {
  const { tokens } = useAuth();
  const [nombre, setNombre] = useState('');
  const [precio, setPrecio] = useState('');
  const [unidad, setUnidad] = useState('unidad');
  const [descripcion, setDescripcion] = useState('');
  const [cantidad, setCantidad] = useState('1');
  const [imagenUrl, setImagenUrl] = useState<string>();
  const [error, setError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const chooseImage = async () => {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      setError('Debes permitir el acceso a tus fotos para agregar una imagen.');
      return;
    }
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      allowsEditing: true,
      aspect: [4, 3],
      quality: 0.7,
      base64: true,
    });
    const asset = result.canceled ? undefined : result.assets[0];
    if (asset?.base64 && asset.mimeType) {
      setImagenUrl(`data:${asset.mimeType};base64,${asset.base64}`);
      setError(null);
    }
  };

  const submit = async () => {
    const numericPrice = Number(precio.replace(',', '.'));
    const numericQuantity = Number(cantidad);
    if (!nombre.trim() || !unidad.trim() || !Number.isFinite(numericPrice) || numericPrice <= 0
      || !Number.isInteger(numericQuantity) || numericQuantity <= 0) {
      setError('Completa nombre, unidad, un precio mayor que cero y una cantidad entera mayor que cero.');
      return;
    }
    if (!tokens?.accessToken) {
      setError('Tu sesión no está disponible.');
      return;
    }
    setIsSaving(true);
    setError(null);
    try {
      await createProducto(tokens.accessToken, {
        nombre: nombre.trim(),
        precio: numericPrice,
        unidad: unidad.trim(),
        descripcion: descripcion.trim() || undefined,
        cantidadDisponible: numericQuantity,
        imagenUrl,
      });
      onCreated();
    } catch (requestError: unknown) {
      setError(requestError instanceof Error ? requestError.message : 'No se pudo publicar el producto.');
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <View style={styles.topBar}>
        <TouchableOpacity onPress={onGoBack}><Text style={styles.backText}>‹ Volver</Text></TouchableOpacity>
        <Text style={styles.topTitle}>Nuevo producto</Text>
        <View style={styles.spacer} />
      </View>
      <View style={styles.card}>
        <Text style={styles.eyebrow}>PUBLICA LO QUE VENDES</Text>
        <Text style={styles.title}>Añade un producto</Text>
        <Text style={styles.subtitle}>Se publicará inmediatamente en tu catálogo.</Text>
        {error && <View style={styles.errorBox}><Text style={styles.errorText}>{error}</Text></View>}

        <TouchableOpacity style={styles.photoBox} onPress={chooseImage} accessibilityRole="button">
          {imagenUrl ? <Image source={{ uri: imagenUrl }} style={styles.photo} /> : (
            <>
              <Text style={styles.photoIcon}>📷</Text>
              <Text style={styles.photoText}>Agregar foto del producto</Text>
              <Text style={styles.photoHint}>Opcional</Text>
            </>
          )}
        </TouchableOpacity>

        <Text style={styles.label}>Nombre *</Text>
        <TextInput value={nombre} onChangeText={setNombre} placeholder="Ej. Empanada de carne" placeholderTextColor="#9CA3AF" style={styles.input} />
        <View style={styles.row}>
          <View style={styles.half}><Text style={styles.label}>Precio *</Text><TextInput value={precio} onChangeText={setPrecio} keyboardType="decimal-pad" placeholder="5000" placeholderTextColor="#9CA3AF" style={styles.input} /></View>
          <View style={styles.half}><Text style={styles.label}>Cantidad *</Text><TextInput value={cantidad} onChangeText={setCantidad} keyboardType="number-pad" placeholder="1" placeholderTextColor="#9CA3AF" style={styles.input} /></View>
        </View>
        <Text style={styles.label}>Unidad *</Text>
        <TextInput value={unidad} onChangeText={setUnidad} placeholder="unidad, porción, libra..." placeholderTextColor="#9CA3AF" style={styles.input} />
        <Text style={styles.label}>Descripción</Text>
        <TextInput value={descripcion} onChangeText={setDescripcion} placeholder="Cuenta qué incluye o cómo es" placeholderTextColor="#9CA3AF" style={[styles.input, styles.multiline]} multiline />
        <TouchableOpacity style={styles.button} onPress={submit} disabled={isSaving}>
          {isSaving ? <ActivityIndicator color="#FFFFFF" /> : <Text style={styles.buttonText}>Publicar producto</Text>}
        </TouchableOpacity>
      </View>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flexGrow: 1, backgroundColor: '#F6F7F9', padding: 18, paddingTop: 34, alignItems: 'center' },
  topBar: { width: '100%', maxWidth: 600, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 22 },
  backText: { color: '#FF5A5F', fontSize: 16, fontWeight: '700' },
  topTitle: { color: '#1F2937', fontSize: 18, fontWeight: '800' },
  spacer: { width: 52 },
  card: { width: '100%', maxWidth: 600, backgroundColor: '#FFFFFF', borderRadius: 20, padding: 22, borderWidth: 1, borderColor: '#ECEEF2', shadowColor: '#AAB1BD', shadowOpacity: 0.14, shadowRadius: 8, elevation: 2 },
  eyebrow: { color: '#FF5A5F', fontSize: 11, fontWeight: '800', letterSpacing: 1.4 },
  title: { color: '#1F2937', fontSize: 27, fontWeight: '800', marginTop: 5 },
  subtitle: { color: '#6B7280', fontSize: 14, marginTop: 5, marginBottom: 12 },
  errorBox: { backgroundColor: '#FFF0F0', borderColor: '#FFB7B9', borderWidth: 1, borderRadius: 10, padding: 11, marginBottom: 10 },
  errorText: { color: '#C2414B', fontSize: 13 },
  photoBox: { height: 150, backgroundColor: '#FFF7E5', borderColor: '#FFD98A', borderRadius: 14, borderWidth: 1, borderStyle: 'dashed', alignItems: 'center', justifyContent: 'center', overflow: 'hidden', marginBottom: 8 },
  photo: { width: '100%', height: '100%' },
  photoIcon: { fontSize: 30 },
  photoText: { color: '#C27803', fontSize: 14, fontWeight: '800', marginTop: 5 },
  photoHint: { color: '#9CA3AF', fontSize: 12, marginTop: 3 },
  label: { color: '#374151', fontSize: 13, fontWeight: '700', marginTop: 12, marginBottom: 6 },
  input: { backgroundColor: '#F8F9FB', borderColor: '#D9DDE5', borderWidth: 1, borderRadius: 11, color: '#1F2937', paddingHorizontal: 13, paddingVertical: 12, fontSize: 14 },
  row: { flexDirection: 'row', gap: 10 },
  half: { flex: 1 },
  multiline: { minHeight: 82, textAlignVertical: 'top' },
  button: { backgroundColor: '#FF5A5F', borderRadius: 12, alignItems: 'center', paddingVertical: 15, marginTop: 24 },
  buttonText: { color: '#FFFFFF', fontSize: 15, fontWeight: '800' },
});
