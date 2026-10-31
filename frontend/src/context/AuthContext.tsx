import React, { createContext, useContext, useEffect, useState } from 'react';
import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';
import { authApi } from '../services/api';
import {
  AuthTokens,
  LoginRequest,
  RegistroRequest,
  UsuarioResumen,
} from '../types/auth';

// En web, SecureStore no está disponible; usamos localStorage como fallback
const storage = {
  async getItem(key: string): Promise<string | null> {
    if (Platform.OS === 'web') {
      return localStorage.getItem(key);
    }
    return SecureStore.getItemAsync(key);
  },
  async setItem(key: string, value: string): Promise<void> {
    if (Platform.OS === 'web') {
      localStorage.setItem(key, value);
      return;
    }
    await SecureStore.setItemAsync(key, value);
  },
  async removeItem(key: string): Promise<void> {
    if (Platform.OS === 'web') {
      localStorage.removeItem(key);
      return;
    }
    await SecureStore.deleteItemAsync(key);
  },
};

const KEYS = {
  ACCESS_TOKEN: 'napa_access_token',
  REFRESH_TOKEN: 'napa_refresh_token',
  EXPIRES_IN: 'napa_expires_in',
  USER: 'napa_user',
};

interface AuthContextType {
  user: UsuarioResumen | null;
  tokens: AuthTokens | null;
  isLoading: boolean;
  isInitializing: boolean;
  error: string | null;
  login: (data: LoginRequest) => Promise<void>;
  register: (data: RegistroRequest) => Promise<void>;
  logout: () => Promise<void>;
  clearError: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const [user, setUser] = useState<UsuarioResumen | null>(null);
  const [tokens, setTokens] = useState<AuthTokens | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isInitializing, setIsInitializing] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const clearError = () => setError(null);

  // Persiste tokens y usuario en almacenamiento seguro
  const persistSession = async (authTokens: AuthTokens) => {
    await storage.setItem(KEYS.ACCESS_TOKEN, authTokens.accessToken);
    await storage.setItem(KEYS.REFRESH_TOKEN, authTokens.refreshToken);
    await storage.setItem(KEYS.EXPIRES_IN, String(authTokens.expiresIn));
    await storage.setItem(KEYS.USER, JSON.stringify(authTokens.usuario));
  };

  // Borra la sesión del almacenamiento seguro
  const clearSession = async () => {
    await storage.removeItem(KEYS.ACCESS_TOKEN);
    await storage.removeItem(KEYS.REFRESH_TOKEN);
    await storage.removeItem(KEYS.EXPIRES_IN);
    await storage.removeItem(KEYS.USER);
  };

  // Al arrancar la app, intentamos restaurar la sesión guardada
  useEffect(() => {
    const restoreSession = async () => {
      try {
        const accessToken = await storage.getItem(KEYS.ACCESS_TOKEN);
        const refreshToken = await storage.getItem(KEYS.REFRESH_TOKEN);
        const expiresIn = await storage.getItem(KEYS.EXPIRES_IN);
        const userJson = await storage.getItem(KEYS.USER);

        if (accessToken && refreshToken && userJson) {
          const savedUser: UsuarioResumen = JSON.parse(userJson);
          // Intentamos renovar el token para verificar que sigue válido
          try {
            const refreshed = await authApi.refresh(refreshToken);
            await persistSession(refreshed);
            setTokens(refreshed);
            setUser(refreshed.usuario);
          } catch {
            // Si el refresh falla (sesión expirada), restauramos igual con el token guardado
            // El usuario tendrá que volver a iniciar sesión cuando haga una petición protegida
            setTokens({
              accessToken,
              refreshToken,
              expiresIn: expiresIn ? Number(expiresIn) : 0,
              usuario: savedUser,
            });
            setUser(savedUser);
          }
        }
      } catch {
        // Si hay error leyendo el storage, iniciamos sesión limpia
        await clearSession();
      } finally {
        setIsInitializing(false);
      }
    };

    restoreSession();
  }, []);

  const login = async (data: LoginRequest) => {
    setIsLoading(true);
    setError(null);
    try {
      const res = await authApi.login(data);
      await persistSession(res);
      setTokens(res);
      setUser(res.usuario);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error al iniciar sesión';
      setError(msg);
      throw err;
    } finally {
      setIsLoading(false);
    }
  };

  const register = async (data: RegistroRequest) => {
    setIsLoading(true);
    setError(null);
    try {
      await authApi.register(data);
      // Tras registrarse con éxito, confirmamos con el código de desarrollo (D12)
      const res = await authApi.confirmPhone(data.celular, '123456');
      await persistSession(res);
      setTokens(res);
      setUser(res.usuario);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error al registrar usuario';
      setError(msg);
      throw err;
    } finally {
      setIsLoading(false);
    }
  };

  const logout = async () => {
    if (tokens?.accessToken) {
      await authApi.logout(tokens.accessToken);
    }
    await clearSession();
    setUser(null);
    setTokens(null);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        tokens,
        isLoading,
        isInitializing,
        error,
        login,
        register,
        logout,
        clearError,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth debe ser usado dentro de AuthProvider');
  }
  return context;
};
