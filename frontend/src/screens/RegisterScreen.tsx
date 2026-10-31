import React, { useState } from 'react';
import {
  ActivityIndicator,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { useAuth } from '../context/AuthContext';
import { RolUsuario } from '../types/auth';

interface RegisterScreenProps {
  onGoToLogin: () => void;
}

interface RoleOption {
  rol: RolUsuario;
  titulo: string;
  subtitulo: string;
  icono: string;
}

const ROLES: RoleOption[] = [
  {
    rol: 'CLIENTE',
    titulo: 'Cliente / Comprador',
    subtitulo: 'Descubre emprendedores cercanos y pide productos',
    icono: '🛒',
  },
  {
    rol: 'VENDEDOR_AMBULANTE',
    titulo: 'Vendedor Ambulante',
    subtitulo: 'Carretilla, termo o puesto móvil (entregas directas)',
    icono: '🛺',
  },
  {
    rol: 'VENDEDOR_PUNTO_FIJO',
    titulo: 'Vendedor Punto Fijo',
    subtitulo: 'Puesto o local fijo (ofrece domicilios y recogidas)',
    icono: '🏪',
  },
  {
    rol: 'DOMICILIARIO',
    titulo: 'Domiciliario',
    subtitulo: 'Reparte pedidos y gana por cada entrega completada',
    icono: '🛵',
  },
];

export const RegisterScreen: React.FC<RegisterScreenProps> = ({ onGoToLogin }) => {
  const { register, isLoading, error, clearError } = useAuth();

  const [nombre, setNombre] = useState('');
  const [celular, setCelular] = useState('+57');
  const [correo, setCorreo] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [rol, setRol] = useState<RolUsuario>('CLIENTE');
  const [localValidation, setLocalValidation] = useState<string | null>(null);

  const handleSubmit = async () => {
    setLocalValidation(null);
    clearError();

    if (!nombre.trim()) {
      setLocalValidation('Por favor ingresa tu nombre completo.');
      return;
    }

    let cel = celular.trim();
    if (!cel.startsWith('+57') && !cel.startsWith('+')) {
      cel = `+57${cel}`;
    }

    if (cel.length < 12) {
      setLocalValidation('Ingresa un número de celular válido (ej. +573001234567).');
      return;
    }

    if (password.length < 8) {
      setLocalValidation('La contraseña debe tener mínimo 8 caracteres.');
      return;
    }

    if (password !== confirmPassword) {
      setLocalValidation('Las contraseñas no coinciden.');
      return;
    }

    try {
      await register({
        nombre: nombre.trim(),
        celular: cel,
        correo: correo.trim() || undefined,
        password,
        confirmPassword,
        rol,
      });
    } catch {
      // El error se maneja en el contexto
    }
  };

  return (
    <ScrollView contentContainerStyle={styles.scrollContainer}>
      <View style={styles.card}>
        <Text style={styles.brandTitle}>Únete a Ñapa</Text>
        <Text style={styles.cardSubtitle}>
          Crea tu cuenta seleccionando tu actividad en la plataforma
        </Text>

        {(localValidation || error) && (
          <View style={styles.errorBox}>
            <Text style={styles.errorText}>{localValidation || error}</Text>
          </View>
        )}

        {/* Selector de Rol */}
        <Text style={styles.sectionLabel}>Elige cómo usarás Ñapa:</Text>
        <View style={styles.rolesGrid}>
          {ROLES.map((r) => {
            const isSelected = rol === r.rol;
            return (
              <TouchableOpacity
                key={r.rol}
                style={[styles.roleCard, isSelected && styles.roleCardSelected]}
                onPress={() => setRol(r.rol)}
              >
                <Text style={styles.roleIcon}>{r.icono}</Text>
                <View style={styles.roleTextBox}>
                  <Text style={[styles.roleTitle, isSelected && styles.roleTitleSelected]}>
                    {r.titulo}
                  </Text>
                  <Text style={styles.roleSub}>{r.subtitulo}</Text>
                </View>
              </TouchableOpacity>
            );
          })}
        </View>

        {/* Campos de Entrada */}
        <Text style={styles.label}>Nombre completo *</Text>
        <TextInput
          style={styles.input}
          placeholder="Ej. Carmen Rodríguez"
          placeholderTextColor="#94A3B8"
          value={nombre}
          onChangeText={setNombre}
        />

        <Text style={styles.label}>Celular (Colombia +57) *</Text>
        <TextInput
          style={styles.input}
          placeholder="+573001234567"
          placeholderTextColor="#94A3B8"
          value={celular}
          onChangeText={setCelular}
          keyboardType="phone-pad"
        />

        <Text style={styles.label}>Correo electrónico (Opcional)</Text>
        <TextInput
          style={styles.input}
          placeholder="carmen@ejemplo.com"
          placeholderTextColor="#94A3B8"
          value={correo}
          onChangeText={setCorreo}
          keyboardType="email-address"
          autoCapitalize="none"
        />

        <Text style={styles.label}>Contraseña * (mínimo 8 caracteres)</Text>
        <TextInput
          style={styles.input}
          placeholder="Crea tu contraseña"
          placeholderTextColor="#94A3B8"
          value={password}
          onChangeText={setPassword}
          secureTextEntry
        />

        <Text style={styles.label}>Confirmar Contraseña *</Text>
        <TextInput
          style={styles.input}
          placeholder="Repite tu contraseña"
          placeholderTextColor="#94A3B8"
          value={confirmPassword}
          onChangeText={setConfirmPassword}
          secureTextEntry
        />

        <TouchableOpacity
          style={[styles.button, isLoading && styles.buttonDisabled]}
          onPress={handleSubmit}
          disabled={isLoading}
        >
          {isLoading ? (
            <ActivityIndicator color="#FFFFFF" />
          ) : (
            <Text style={styles.buttonText}>Crear mi cuenta</Text>
          )}
        </TouchableOpacity>

        <TouchableOpacity style={styles.linkButton} onPress={onGoToLogin}>
          <Text style={styles.linkText}>
            ¿Ya tienes cuenta? <Text style={styles.linkTextBold}>Inicia sesión</Text>
          </Text>
        </TouchableOpacity>
      </View>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  scrollContainer: {
    flexGrow: 1,
    backgroundColor: '#F6F7F9',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
    paddingVertical: 40,
  },
  card: {
    backgroundColor: '#FFFFFF',
    borderRadius: 24,
    padding: 28,
    width: '100%',
    maxWidth: 440,
    borderWidth: 1,
    borderColor: '#ECEEF2',
    shadowColor: '#AAB1BD',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.16,
    shadowRadius: 18,
    elevation: 6,
  },
  brandTitle: {
    fontSize: 30,
    fontWeight: '800',
    color: '#FF5A5F',
    textAlign: 'center',
  },
  cardSubtitle: {
    fontSize: 14,
    color: '#6B7280',
    textAlign: 'center',
    marginTop: 6,
    marginBottom: 24,
  },
  sectionLabel: {
    fontSize: 14,
    fontWeight: '700',
    color: '#1F2937',
    marginBottom: 10,
  },
  rolesGrid: {
    gap: 8,
    marginBottom: 16,
  },
  roleCard: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 12,
    borderWidth: 1.5,
    borderColor: '#D9DDE5',
    borderRadius: 14,
    backgroundColor: '#F8F9FB',
  },
  roleCardSelected: {
    borderColor: '#FF5A5F',
    backgroundColor: '#FFF0F0',
  },
  roleIcon: {
    fontSize: 26,
    marginRight: 12,
  },
  roleTextBox: {
    flex: 1,
  },
  roleTitle: {
    fontSize: 14,
    fontWeight: '700',
    color: '#1F2937',
  },
  roleTitleSelected: {
    color: '#C2414B',
  },
  roleSub: {
    fontSize: 12,
    color: '#6B7280',
    marginTop: 2,
  },
  label: {
    fontSize: 13,
    fontWeight: '600',
    color: '#374151',
    marginBottom: 4,
    marginTop: 10,
  },
  input: {
    backgroundColor: '#F8F9FB',
    borderWidth: 1,
    borderColor: '#D9DDE5',
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 12,
    fontSize: 14,
    color: '#1F2937',
  },
  errorBox: {
    backgroundColor: '#FFF0F0',
    padding: 12,
    borderRadius: 8,
    marginBottom: 14,
    borderWidth: 1,
    borderColor: '#FFB7B9',
  },
  errorText: {
    color: '#C2414B',
    fontSize: 13,
    fontWeight: '500',
  },
  button: {
    backgroundColor: '#FF5A5F',
    paddingVertical: 15,
    borderRadius: 12,
    alignItems: 'center',
    marginTop: 20,
  },
  buttonDisabled: {
    opacity: 0.6,
  },
  buttonText: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '700',
  },
  linkButton: {
    marginTop: 16,
    alignItems: 'center',
  },
  linkText: {
    color: '#6B7280',
    fontSize: 13,
  },
  linkTextBold: {
    color: '#FF5A5F',
    fontWeight: '700',
  },
});
