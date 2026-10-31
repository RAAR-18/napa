import React from 'react';
import {
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { useAuth } from '../context/AuthContext';
import { RolUsuario } from '../types/auth';

interface ProfileScreenProps {
  onGoBack: () => void;
}

const roleNames: Record<RolUsuario, string> = {
  CLIENTE: 'Cliente / Comprador',
  VENDEDOR_AMBULANTE: 'Vendedor ambulante',
  VENDEDOR_PUNTO_FIJO: 'Vendedor de punto fijo',
  DOMICILIARIO: 'Domiciliario',
  ADMINISTRADOR: 'Administrador',
};

export const ProfileScreen: React.FC<ProfileScreenProps> = ({ onGoBack }) => {
  const { user, logout } = useAuth();

  if (!user) {
    return null;
  }

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={onGoBack} accessibilityRole="button">
          <Text style={styles.backText}>‹ Volver</Text>
        </TouchableOpacity>
        <Text accessibilityRole="header" style={styles.title}>Mi perfil</Text>
        <View style={styles.headerSpacer} />
      </View>

      <View style={styles.avatar}>
        <Text style={styles.avatarText}>{user.nombre.charAt(0).toUpperCase()}</Text>
      </View>
      <Text style={styles.name}>{user.nombre}</Text>
      <Text style={styles.role}>{roleNames[user.rol]}</Text>

      <View style={styles.detailsCard}>
        <Text style={styles.cardTitle}>Datos de la cuenta</Text>
        <View style={styles.detailRow}>
          <Text style={styles.detailLabel}>Nombre</Text>
          <Text style={styles.detailValue}>{user.nombre}</Text>
        </View>
        <View style={styles.detailRow}>
          <Text style={styles.detailLabel}>Rol</Text>
          <Text style={styles.detailValue}>{roleNames[user.rol]}</Text>
        </View>
        <View style={styles.detailRow}>
          <Text style={styles.detailLabel}>Estado</Text>
          <Text style={styles.activeValue}>Cuenta activa</Text>
        </View>
      </View>

      <TouchableOpacity style={styles.logoutButton} onPress={logout}>
        <Text style={styles.logoutText}>Cerrar sesión</Text>
      </TouchableOpacity>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F6F7F9',
    padding: 24,
    paddingTop: 56,
    alignItems: 'center',
  },
  header: {
    width: '100%',
    maxWidth: 600,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 42,
  },
  backText: {
    color: '#FF5A5F',
    fontSize: 16,
    fontWeight: '700',
  },
  title: {
    color: '#1F2937',
    fontSize: 22,
    fontWeight: '800',
  },
  headerSpacer: {
    width: 64,
  },
  avatar: {
    width: 88,
    height: 88,
    borderRadius: 44,
    backgroundColor: '#FFF0D0',
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarText: {
    color: '#FF5A5F',
    fontSize: 36,
    fontWeight: '800',
  },
  name: {
    color: '#1F2937',
    fontSize: 26,
    fontWeight: '800',
    marginTop: 16,
  },
  role: {
    color: '#6B7280',
    fontSize: 15,
    marginTop: 4,
  },
  detailsCard: {
    width: '100%',
    maxWidth: 600,
    backgroundColor: '#FFFFFF',
    borderRadius: 20,
    padding: 22,
    marginTop: 32,
    borderWidth: 1,
    borderColor: '#ECEEF2',
    shadowColor: '#AAB1BD',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.16,
    shadowRadius: 12,
    elevation: 3,
  },
  cardTitle: {
    color: '#1F2937',
    fontSize: 17,
    fontWeight: '800',
    marginBottom: 16,
  },
  detailRow: {
    borderTopWidth: 1,
    borderTopColor: '#ECEEF2',
    paddingVertical: 15,
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  detailLabel: {
    color: '#6B7280',
    fontSize: 14,
  },
  detailValue: {
    color: '#1F2937',
    fontSize: 14,
    fontWeight: '600',
    maxWidth: '60%',
    textAlign: 'right',
  },
  activeValue: {
    color: '#16A34A',
    fontSize: 14,
    fontWeight: '700',
  },
  logoutButton: {
    width: '100%',
    maxWidth: 600,
    marginTop: 24,
    paddingVertical: 14,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#EF4444',
    alignItems: 'center',
  },
  logoutText: {
    color: '#FF5A5F',
    fontSize: 15,
    fontWeight: '700',
  },
});
