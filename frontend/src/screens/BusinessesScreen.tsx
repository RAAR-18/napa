import React, { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Image,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { useAuth } from '../context/AuthContext';
import { EmprendimientoListItem, listEmprendimientos } from '../services/api';

interface BusinessesScreenProps {
  onGoBack: () => void;
}

const categoryNames: Record<string, string> = {
  ALIMENTOS_PREPARADOS: 'Alimentos preparados',
  FRUTAS_VERDURAS: 'Frutas y verduras',
  BEBIDAS: 'Bebidas',
  ROPA_ACCESORIOS: 'Ropa y accesorios',
  ARTESANIAS: 'Artesanías',
  OTROS: 'Otros',
};

const sellerNames: Record<string, string> = {
  VENDEDOR_AMBULANTE: 'Vendedor ambulante',
  VENDEDOR_PUNTO_FIJO: 'Punto fijo',
};

export const BusinessesScreen: React.FC<BusinessesScreenProps> = ({ onGoBack }) => {
  const { tokens } = useAuth();
  const [businesses, setBusinesses] = useState<EmprendimientoListItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!tokens?.accessToken) {
      setError('Tu sesión no está disponible.');
      setIsLoading(false);
      return;
    }
    listEmprendimientos(tokens.accessToken)
      .then((result) => setBusinesses(result.content))
      .catch((requestError: unknown) => {
        setError(requestError instanceof Error ? requestError.message : 'No se pudo cargar el listado.');
      })
      .finally(() => setIsLoading(false));
  }, [tokens?.accessToken]);

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <View style={styles.topBar}>
        <TouchableOpacity onPress={onGoBack}>
          <Text style={styles.backText}>‹ Volver</Text>
        </TouchableOpacity>
        <Text style={styles.title}>Comercios cercanos</Text>
        <View style={styles.spacer} />
      </View>

      <View style={styles.intro}>
        <Text style={styles.eyebrow}>DESCUBRE LO LOCAL</Text>
        <Text style={styles.heading}>Emprendimientos disponibles</Text>
        <Text style={styles.subtitle}>Conoce vendedores y negocios de tu comunidad.</Text>
      </View>

      {isLoading && <ActivityIndicator color="#FF5A5F" size="large" style={styles.loader} />}
      {error && <View style={styles.message}><Text style={styles.errorText}>{error}</Text></View>}
      {!isLoading && !error && businesses.length === 0 && (
        <View style={styles.message}>
          <Text style={styles.emptyIcon}>🛍️</Text>
          <Text style={styles.emptyTitle}>No hay emprendimientos disponibles</Text>
          <Text style={styles.emptyText}>Cuando haya vendedores registrados, aparecerán aquí.</Text>
        </View>
      )}
      {!isLoading && !error && businesses.map((business) => (
        <View key={business.id} style={styles.businessCard}>
          <View style={styles.cardTop}>
          {business.imagenUrl ? (
            <Image source={{ uri: business.imagenUrl }} style={styles.businessImage} />
          ) : (
            <View style={styles.iconTile}><Text style={styles.icon}>🏪</Text></View>
          )}
            <View style={styles.cardHeading}>
              <Text style={styles.businessName}>{business.nombre}</Text>
              <Text style={styles.category}>{categoryNames[business.categoria] || business.categoria}</Text>
            </View>
            <View style={[styles.status, business.abierto ? styles.open : styles.closed]}>
              <Text style={styles.statusText}>{business.abierto ? 'Abierto' : 'Cerrado'}</Text>
            </View>
          </View>
          <Text style={styles.description}>{business.descripcion || 'Emprendimiento local de Ñapa.'}</Text>
          <View style={styles.metaRow}>
            <Text style={styles.meta}>📍 {business.direccion}</Text>
            <Text style={styles.meta}>{sellerNames[business.tipoVendedor] || business.tipoVendedor}</Text>
          </View>
          <Text style={styles.schedule}>Horario: {business.horaApertura} - {business.horaCierre}</Text>
        </View>
      ))}
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flexGrow: 1, backgroundColor: '#F6F7F9', padding: 18, paddingTop: 34, alignItems: 'center' },
  topBar: { width: '100%', maxWidth: 600, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 26 },
  backText: { color: '#FF5A5F', fontSize: 16, fontWeight: '700' },
  title: { color: '#1F2937', fontSize: 18, fontWeight: '800' },
  spacer: { width: 52 },
  intro: { width: '100%', maxWidth: 600, marginBottom: 22 },
  eyebrow: { color: '#FF5A5F', fontSize: 11, fontWeight: '800', letterSpacing: 1.5 },
  heading: { color: '#1F2937', fontSize: 27, fontWeight: '800', marginTop: 6 },
  subtitle: { color: '#6B7280', fontSize: 14, marginTop: 6 },
  loader: { marginTop: 50 },
  message: { width: '100%', maxWidth: 600, backgroundColor: '#FFFFFF', borderRadius: 18, padding: 28, alignItems: 'center', borderWidth: 1, borderColor: '#ECEEF2' },
  emptyIcon: { fontSize: 38, marginBottom: 12 },
  emptyTitle: { color: '#1F2937', fontSize: 17, fontWeight: '800', textAlign: 'center' },
  emptyText: { color: '#6B7280', fontSize: 14, textAlign: 'center', marginTop: 8 },
  errorText: { color: '#FDA4AF', fontSize: 14 },
  businessCard: { width: '100%', maxWidth: 600, backgroundColor: '#FFFFFF', borderRadius: 18, padding: 18, marginBottom: 12, borderWidth: 1, borderColor: '#ECEEF2', shadowColor: '#AAB1BD', shadowOpacity: 0.14, shadowRadius: 8, elevation: 2 },
  cardTop: { flexDirection: 'row', alignItems: 'center' },
  iconTile: { width: 48, height: 48, borderRadius: 14, backgroundColor: '#FFF0D0', alignItems: 'center', justifyContent: 'center', marginRight: 12 },
  businessImage: { width: 48, height: 48, borderRadius: 14, marginRight: 12 },
  icon: { fontSize: 24 },
  cardHeading: { flex: 1 },
  businessName: { color: '#1F2937', fontSize: 16, fontWeight: '800' },
  category: { color: '#FF5A5F', fontSize: 13, marginTop: 3 },
  status: { borderRadius: 10, paddingHorizontal: 8, paddingVertical: 5 },
  open: { backgroundColor: '#166534' },
  closed: { backgroundColor: '#475569' },
  statusText: { color: '#FFFFFF', fontSize: 11, fontWeight: '700' },
  description: { color: '#6B7280', fontSize: 13, lineHeight: 19, marginTop: 14 },
  metaRow: { marginTop: 14, gap: 5 },
  meta: { color: '#6B7280', fontSize: 12 },
  schedule: { color: '#FF5A5F', fontSize: 12, marginTop: 10, fontWeight: '600' },
});
