import React, { useState } from 'react';
import {
  ActivityIndicator,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { useAuth } from '../context/AuthContext';

interface LoginScreenProps {
  onGoToRegister: () => void;
}

export const LoginScreen: React.FC<LoginScreenProps> = ({ onGoToRegister }) => {
  const { login, isLoading, error, clearError } = useAuth();
  const [identificador, setIdentificador] = useState('');
  const [password, setPassword] = useState('');

  const handleSubmit = async () => {
    if (!identificador.trim() || !password) {
      return;
    }
    clearError();
    try {
      await login({ identificador: identificador.trim(), password });
    } catch {
      // El error ya queda en el contexto
    }
  };

  return (
    <View style={styles.container}>
      <View style={styles.brandBox}>
        <View style={styles.logoMark}>
          <Text style={styles.logoMarkText}>Ñ</Text>
        </View>
        <Text accessibilityRole="header" style={styles.title}>Ñapa</Text>
        <View style={styles.brandRule} />
        <Text style={styles.description}>
          Compra cerca. Vende mejor.
        </Text>
        <Text style={styles.descriptionSecondary}>
          Conecta a vendedores locales con clientes cercanos.
        </Text>
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Iniciar Sesión</Text>

        {error && (
          <View style={styles.errorBox}>
            <Text style={styles.errorText}>{error}</Text>
          </View>
        )}

        <Text style={styles.label}>Celular o Correo</Text>
        <TextInput
          style={styles.input}
          placeholder="+573001234567 o correo@ejemplo.com"
          placeholderTextColor="#94A3B8"
          value={identificador}
          onChangeText={setIdentificador}
          autoCapitalize="none"
          keyboardType="email-address"
        />

        <Text style={styles.label}>Contraseña</Text>
        <TextInput
          style={styles.input}
          placeholder="Tu contraseña"
          placeholderTextColor="#94A3B8"
          value={password}
          onChangeText={setPassword}
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
            <Text style={styles.buttonText}>Entrar a Ñapa</Text>
          )}
        </TouchableOpacity>

        <TouchableOpacity style={styles.linkButton} onPress={onGoToRegister}>
          <Text style={styles.linkText}>
            ¿No tienes cuenta aún? <Text style={styles.linkTextBold}>Regístrate aquí</Text>
          </Text>
        </TouchableOpacity>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F6F7F9',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  brandBox: {
    alignItems: 'center',
    marginBottom: 30,
  },
  logoMark: {
    width: 58,
    height: 58,
    borderRadius: 19,
    backgroundColor: '#FF5A5F',
    borderWidth: 1,
    borderColor: '#FFB7B9',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 10,
    transform: [{ rotate: '-6deg' }],
  },
  logoMarkText: {
    color: '#FFFFFF',
    fontSize: 38,
    fontWeight: '900',
    transform: [{ rotate: '6deg' }],
  },
  title: {
    color: '#1F2937',
    fontSize: 44,
    fontWeight: '900',
    letterSpacing: -1,
  },
  brandRule: {
    width: 42,
    height: 3,
    borderRadius: 2,
    backgroundColor: '#FF5A5F',
    marginTop: 12,
    marginBottom: 12,
  },
  description: {
    color: '#F8FAFC',
    fontSize: 17,
    fontWeight: '700',
    lineHeight: 24,
    textAlign: 'center',
    maxWidth: 320,
  },
  descriptionSecondary: {
    color: '#6B7280',
    fontSize: 13,
    lineHeight: 20,
    marginTop: 3,
    textAlign: 'center',
    maxWidth: 320,
  },
  card: {
    backgroundColor: '#FFFFFF',
    borderRadius: 24,
    padding: 28,
    width: '100%',
    maxWidth: 400,
    borderWidth: 1,
    borderColor: '#ECEEF2',
    shadowColor: '#AAB1BD',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.16,
    shadowRadius: 18,
    elevation: 6,
  },
  cardTitle: {
    fontSize: 24,
    fontWeight: '800',
    color: '#1F2937',
    marginBottom: 20,
  },
  errorBox: {
    backgroundColor: '#FFF0F0',
    padding: 12,
    borderRadius: 8,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: '#FFB7B9',
  },
  errorText: {
    color: '#C2414B',
    fontSize: 14,
    fontWeight: '500',
  },
  label: {
    fontSize: 14,
    fontWeight: '600',
    color: '#374151',
    marginBottom: 6,
    marginTop: 10,
  },
  input: {
    backgroundColor: '#F8F9FB',
    borderWidth: 1,
    borderColor: '#D9DDE5',
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 14,
    fontSize: 15,
    color: '#1F2937',
  },
  button: {
    backgroundColor: '#FF5A5F',
    paddingVertical: 15,
    borderRadius: 12,
    alignItems: 'center',
    marginTop: 26,
  },
  buttonDisabled: {
    opacity: 0.6,
  },
  buttonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '700',
  },
  linkButton: {
    marginTop: 22,
    alignItems: 'center',
  },
  linkText: {
    color: '#6B7280',
    fontSize: 14,
  },
  linkTextBold: {
    color: '#FF5A5F',
    fontWeight: '700',
  },
});
