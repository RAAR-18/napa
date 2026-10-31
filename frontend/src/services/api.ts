import { Platform } from 'react-native';
import {
  AuthTokens,
  LoginRequest,
  RegistroRequest,
  RegistroResponse,
} from '../types/auth';

export interface CrearEmprendimientoRequest {
  nombre: string;
  categoria: string;
  descripcion?: string;
  imagenUrl?: string;
  horaApertura: string;
  horaCierre: string;
  ubicacion: {
    latitud: number;
    longitud: number;
    direccion: string;
    referencia?: string;
  };
}

export interface EmprendimientoListItem {
  id: string;
  nombre: string;
  categoria: string;
  imagenUrl?: string;
  tipoVendedor: string;
  descripcion?: string;
  horaApertura: string;
  horaCierre: string;
  abierto: boolean;
  latitud: number;
  longitud: number;
  direccion: string;
  referencia?: string;
}

export interface EmprendimientoPage {
  content: EmprendimientoListItem[];
  page: number;
  size: number;
  totalElements: number;
}

export interface CrearProductoRequest {
  nombre: string;
  precio: number;
  unidad: string;
  descripcion?: string;
  cantidadDisponible: number;
  imagenUrl?: string;
}

export interface ProductoListItem {
  id: string;
  emprendimientoId: string;
  emprendimientoNombre?: string;
  nombre: string;
  precio: number;
  unidad: string;
  descripcion?: string;
  cantidadDisponible: number;
  imagenUrl?: string;
}

export interface ProductoPage {
  content: ProductoListItem[];
  page: number;
  size: number;
  totalElements: number;
}

// Generador de UUID para cabecera obligatoria Idempotency-Key (RNF27)
export const generateUUID = (): string => {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
};

// En un dispositivo físico, EXPO_PUBLIC_API_URL debe apuntar a la IP del computador.
const getBaseUrl = (): string => {
  const configuredUrl = process.env.EXPO_PUBLIC_API_URL?.trim();
  if (configuredUrl) {
    return configuredUrl.replace(/\/+$/, '');
  }
  if (Platform.OS === 'android') {
    return 'http://10.0.2.2:8080/api/v1';
  }
  return 'http://localhost:8080/api/v1';
};

export const API_BASE_URL = getBaseUrl();

// Device ID simple y estable para la sesión
let deviceIdCache = 'dev-device-' + Math.random().toString(36).substring(2, 12);

export const getDeviceId = (): string => deviceIdCache;

const defaultHeaders = (token?: string, idempotencyKey?: string) => {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-Device-Id': getDeviceId(),
    'Accept-Language': 'es-CO',
  };
  if (idempotencyKey) {
    headers['Idempotency-Key'] = idempotencyKey;
  }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return headers;
};

export const authApi = {
  async register(data: RegistroRequest): Promise<RegistroResponse> {
    const res = await fetch(`${API_BASE_URL}/auth/register`, {
      method: 'POST',
      headers: defaultHeaders(undefined, generateUUID()),
      body: JSON.stringify(data),
    });

    const body = await res.json().catch(() => ({}));
    if (!res.ok) {
      throw new Error(body.message || `Error al registrarse (${res.status})`);
    }
    return body as RegistroResponse;
  },

  async confirmPhone(celular: string, codigo: string = '123456'): Promise<AuthTokens> {
    const res = await fetch(`${API_BASE_URL}/auth/verify-phone/confirm`, {
      method: 'POST',
      headers: defaultHeaders(undefined, generateUUID()),
      body: JSON.stringify({ celular, codigo }),
    });

    const body = await res.json().catch(() => ({}));
    if (!res.ok) {
      throw new Error(body.message || `Error al verificar celular (${res.status})`);
    }
    return body as AuthTokens;
  },

  async login(data: LoginRequest): Promise<AuthTokens> {
    const res = await fetch(`${API_BASE_URL}/auth/login`, {
      method: 'POST',
      headers: defaultHeaders(),
      body: JSON.stringify(data),
    });

    const body = await res.json().catch(() => ({}));
    if (!res.ok) {
      throw new Error(body.message || `Credenciales inválidas (${res.status})`);
    }
    return body as AuthTokens;
  },

  async refresh(refreshToken: string): Promise<AuthTokens> {
    const res = await fetch(`${API_BASE_URL}/auth/refresh`, {
      method: 'POST',
      headers: defaultHeaders(undefined, generateUUID()),
      body: JSON.stringify({ refreshToken }),
    });

    const body = await res.json().catch(() => ({}));
    if (!res.ok) {
      throw new Error(body.message || `No se pudo renovar la sesión (${res.status})`);
    }
    return body as AuthTokens;
  },

  async logout(token: string): Promise<void> {
    await fetch(`${API_BASE_URL}/auth/logout`, {
      method: 'POST',
      headers: defaultHeaders(token),
    }).catch(() => {
      // Ignorar errores de red en logout
    });
  },
};

export async function createEmprendimiento(
  token: string,
  data: CrearEmprendimientoRequest
): Promise<void> {
  const res = await fetch(`${API_BASE_URL}/emprendimientos`, {
    method: 'POST',
    headers: defaultHeaders(token, generateUUID()),
    body: JSON.stringify(data),
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(body.message || `Error al crear el emprendimiento (${res.status})`);
  }
}

export async function listEmprendimientos(token: string): Promise<EmprendimientoPage> {
  const res = await fetch(`${API_BASE_URL}/emprendimientos?page=0&size=20`, {
    headers: defaultHeaders(token),
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(body.message || `Error al cargar emprendimientos (${res.status})`);
  }
  return body as EmprendimientoPage;
}

export async function checkApiHealth(): Promise<boolean> {
  try {
    const res = await fetch(`${API_BASE_URL.replace('/api/v1', '')}/actuator/health`);
    return res.ok;
  } catch {
    return false;
  }
}

export async function createProducto(
    token: string,
    data: CrearProductoRequest
  ): Promise<ProductoListItem> {
    const res = await fetch(`${API_BASE_URL}/productos`, {
      method: 'POST',
      headers: defaultHeaders(token, generateUUID()),
      body: JSON.stringify(data),
    });
    const body = await res.json().catch(() => ({}));
    if (!res.ok) {
      throw new Error(body.message || `Error al publicar el producto (${res.status})`);
    }
    return body as ProductoListItem;
  }

export async function listMyProductos(token: string): Promise<ProductoPage> {
    const res = await fetch(`${API_BASE_URL}/productos/mios?page=0&size=100`, {
      headers: defaultHeaders(token),
    });
    const body = await res.json().catch(() => ({}));
    if (!res.ok) {
      throw new Error(body.message || `Error al cargar tus productos (${res.status})`);
    }
    return body as ProductoPage;
  }

export async function listProductos(token: string): Promise<ProductoPage> {
    const res = await fetch(`${API_BASE_URL}/productos?page=0&size=50`, {
      headers: defaultHeaders(token),
    });
    const body = await res.json().catch(() => ({}));
    if (!res.ok) {
      throw new Error(body.message || `Error al cargar productos (${res.status})`);
    }
    return body as ProductoPage;
}
