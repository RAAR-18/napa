export type RolUsuario =
  | 'CLIENTE'
  | 'VENDEDOR_AMBULANTE'
  | 'VENDEDOR_PUNTO_FIJO'
  | 'DOMICILIARIO'
  | 'ADMINISTRADOR';

export interface UsuarioResumen {
  id: string;
  nombre: string;
  rol: RolUsuario;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  usuario: UsuarioResumen;
}

export interface RegistroRequest {
  nombre: string;
  celular: string;
  correo?: string;
  password: string;
  confirmPassword: string;
  rol: RolUsuario;
}

export interface RegistroResponse {
  usuarioId: string;
  estado: string;
}

export interface LoginRequest {
  identificador: string;
  password: string;
}

export interface ApiErrorResponse {
  code: string;
  message: string;
  details?: Record<string, unknown>;
  traceId?: string;
}

