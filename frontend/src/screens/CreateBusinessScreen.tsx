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
import { createEmprendimiento } from '../services/api';

interface CreateBusinessScreenProps {
  onGoBack: () => void;
  onCreated: () => void;
}

const CATEGORIES = [
  ['ALIMENTOS_PREPARADOS', 'Alimentos preparados'],
  ['FRUTAS_VERDURAS', 'Frutas y verduras'],
  ['BEBIDAS', 'Bebidas'],
  ['ROPA_ACCESORIOS', 'Ropa y accesorios'],
  ['ARTESANIAS', 'Artesanías'],
  ['OTROS', 'Otros'],
] as const;

export const CreateBusinessScreen: React.FC<CreateBusinessScreenProps> = ({
  onGoBack,
  onCreated,
}) => {
  const { tokens } = useAuth();
  const [nombre, setNombre] = useState('');
  const [categoria, setCategoria] = useState('ALIMENTOS_PREPARADOS');
  const [descripcion, setDescripcion] = useState('');
  const [imagenUrl, setImagenUrl] = useState<string>();
  const [direccion, setDireccion] = useState('');
  const [referencia, setReferencia] = useState('');
  const [horaApertura, setHoraApertura] = useState('06:00');
  const [horaCierre, setHoraCierre] = useState('18:00');
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const chooseImage = async () => {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      setError('Debes permitir el acceso a tus fotos para agregar el logo.');
      return;
    }
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      allowsEditing: true,
      aspect: [1, 1],
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
    if (!tokens?.accessToken) {
      setError('Tu sesión no está disponible. Inicia sesión nuevamente.');
      return;
    }
    if (!nombre.trim() || !direccion.trim()) {
      setError('Completa el nombre y la dirección de tu emprendimiento.');
      return;
    }
    setError(null);
    setIsLoading(true);
    try {
      await createEmprendimiento(tokens.accessToken, {
        nombre: nombre.trim(),
        categoria,
        descripcion: descripcion.trim() || undefined,
        imagenUrl,
        horaApertura,
        horaCierre,
        ubicacion: {
          // Ubicación aproximada temporal para la demo; el mapa la reemplazará.
          latitud: 11.2408,
          longitud: -74.2110,
          direccion: direccion.trim(),
          referencia: referencia.trim() || undefined,
        },
      });
      onCreated();
    } catch (requestError: unknown) {
      setError(requestError instanceof Error ? requestError.message : 'No se pudo crear el emprendimiento.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <View style={styles.topBar}>
        <TouchableOpacity onPress={onGoBack}>
          <Text style={styles.backText}>‹ Volver</Text>
        </TouchableOpacity>
        <Text style={styles.topTitle}>Nuevo emprendimiento</Text>
        <View style={styles.spacer} />
      </View>

      <View style={styles.card}>
        <Text style={styles.eyebrow}>PUBLICA TU NEGOCIO</Text>
        <Text style={styles.title}>Crea tu emprendimiento</Text>
        <Text style={styles.subtitle}>Completa estos datos para que tus clientes puedan encontrarte.</Text>

        {error && <View style={styles.errorBox}><Text style={styles.errorText}>{error}</Text></View>}

        <Text style={styles.label}>Nombre del emprendimiento *</Text>
        <TextInput style={styles.input} placeholder="Ej. Frutas Doña Rosa" placeholderTextColor="#94A3B8" value={nombre} onChangeText={setNombre} />

        <Text style={styles.label}>Foto o logo del emprendimiento</Text>
        <TouchableOpacity style={styles.logoPicker} onPress={chooseImage} accessibilityRole="button">
          {imagenUrl ? <Image source={{ uri: imagenUrl }} style={styles.logoPreview} /> : (
            <>
              <Text style={styles.logoIcon}>🏪</Text>
              <Text style={styles.logoText}>Agregar foto o logo</Text>
              <Text style={styles.logoHint}>Opcional</Text>
            </>
          )}
        </TouchableOpacity>

        <Text style={styles.label}>Categoría *</Text>
        <View style={styles.categoryGrid}>
          {CATEGORIES.map(([value, label]) => (
            <TouchableOpacity
              key={value}
              style={[styles.category, categoria === value && styles.categorySelected]}
              onPress={() => setCategoria(value)}
            >
              <Text style={[styles.categoryText, categoria === value && styles.categoryTextSelected]}>{label}</Text>
            </TouchableOpacity>
          ))}
        </View>

        <Text style={styles.label}>Descripción</Text>
        <TextInput style={[styles.input, styles.multiline]} placeholder="Cuéntales qué ofreces" placeholderTextColor="#94A3B8" value={descripcion} onChangeText={setDescripcion} multiline />

        <Text style={styles.label}>Dirección o zona de venta *</Text>
        <TextInput style={styles.input} placeholder="Ej. Calle 15 # 3-20" placeholderTextColor="#94A3B8" value={direccion} onChangeText={setDireccion} />

        <Text style={styles.label}>Referencia</Text>
        <TextInput style={styles.input} placeholder="Ej. Frente al parque" placeholderTextColor="#94A3B8" value={referencia} onChangeText={setReferencia} />

        <View style={styles.row}>
          <View style={styles.half}>
            <Text style={styles.label}>Abre</Text>
            <TextInput style={styles.input} value={horaApertura} onChangeText={setHoraApertura} placeholder="06:00" placeholderTextColor="#94A3B8" />
          </View>
          <View style={styles.half}>
            <Text style={styles.label}>Cierra</Text>
            <TextInput style={styles.input} value={horaCierre} onChangeText={setHoraCierre} placeholder="18:00" placeholderTextColor="#94A3B8" />
          </View>
        </View>

        <TouchableOpacity style={[styles.button, isLoading && styles.disabled]} onPress={submit} disabled={isLoading}>
          {isLoading ? <ActivityIndicator color="#FFFFFF" /> : <Text style={styles.buttonText}>Publicar emprendimiento</Text>}
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
  card: { width: '100%', maxWidth: 600, backgroundColor: '#FFFFFF', borderRadius: 20, padding: 24, borderWidth: 1, borderColor: '#ECEEF2', shadowColor: '#AAB1BD', shadowOpacity: 0.14, shadowRadius: 8, elevation: 2 },
  eyebrow: { color: '#FF5A5F', fontSize: 11, fontWeight: '800', letterSpacing: 1.5 },
  title: { color: '#1F2937', fontSize: 27, fontWeight: '800', marginTop: 6 },
  subtitle: { color: '#6B7280', fontSize: 14, lineHeight: 21, marginTop: 6, marginBottom: 12 },
  errorBox: { backgroundColor: '#FFF0F0', borderColor: '#FFB7B9', borderWidth: 1, borderRadius: 10, padding: 12, marginBottom: 8 },
  errorText: { color: '#C2414B', fontSize: 14 },
  label: { color: '#374151', fontSize: 13, fontWeight: '700', marginTop: 12, marginBottom: 6 },
  input: { backgroundColor: '#F8F9FB', borderColor: '#D9DDE5', borderWidth: 1, borderRadius: 11, color: '#1F2937', paddingHorizontal: 13, paddingVertical: 12, fontSize: 14 },
  logoPicker: { width: 130, height: 130, backgroundColor: '#FFF7E5', borderColor: '#FFD98A', borderRadius: 65, borderWidth: 1, borderStyle: 'dashed', alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  logoPreview: { width: '100%', height: '100%' },
  logoIcon: { fontSize: 30 },
  logoText: { color: '#C27803', fontSize: 12, fontWeight: '800', marginTop: 4, textAlign: 'center' },
  logoHint: { color: '#9CA3AF', fontSize: 11, marginTop: 2 },
  multiline: { minHeight: 76, textAlignVertical: 'top' },
  categoryGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  category: { borderWidth: 1, borderColor: '#D9DDE5', borderRadius: 10, paddingHorizontal: 11, paddingVertical: 9 },
  categorySelected: { backgroundColor: '#FFF0F0', borderColor: '#FF5A5F' },
  categoryText: { color: '#6B7280', fontSize: 12, fontWeight: '600' },
  categoryTextSelected: { color: '#C2414B' },
  row: { flexDirection: 'row', gap: 12 },
  half: { flex: 1 },
  button: { backgroundColor: '#FF5A5F', borderRadius: 12, alignItems: 'center', paddingVertical: 15, marginTop: 24 },
  disabled: { opacity: 0.6 },
  buttonText: { color: '#FFFFFF', fontSize: 15, fontWeight: '800' },
});
