import * as Crypto from 'expo-crypto';

import { ApiError, jsonHeaders, requestJson, requestVoid } from './api';
import { clearSession, withValidSession } from './auth';
import { router } from 'expo-router';

export const unidadesProducto = [
  { value: 'UNIDAD', label: 'Unidad' },
  { value: 'KG', label: 'Kilogramo' },
  { value: 'LIBRA', label: 'Libra' },
  { value: 'LITRO', label: 'Litro' },
  { value: 'PORCION', label: 'Porción' },
] as const;

export type UnidadProducto = (typeof unidadesProducto)[number]['value'];
export type Producto = {
  id: string;
  emprendimientoId: string;
  nombre: string;
  precio: number;
  unidad: UnidadProducto;
  descripcion: string | null;
  cantidadDisponible: number;
  estado: 'DISPONIBLE' | 'AGOTADO';
  soloReserva: boolean;
  updatedAt: string;
  version: number;
};
export type CrearProductoInput = {
  nombre: string;
  precio: number;
  unidad: UnidadProducto;
  descripcion: string | null;
  cantidadDisponible: number;
};
export type ProductosPage = {
  content: Producto[];
  page: number;
  size: number;
  totalElements: number;
  emprendimiento: { id: string; nombre: string } | null;
};
export type OrdenProductos = 'nombre,asc' | 'nombre,desc' | 'precio,asc' | 'precio,desc';

function list(path: string, page: number, sort: OrdenProductos): Promise<ProductosPage> {
  const query = new URLSearchParams({ page: String(page), size: '20', sort });
  return withValidSession((session) => requestJson<ProductosPage>(`${path}?${query}`, {
    method: 'GET', headers: jsonHeaders({ accessToken: session.accessToken }),
  }));
}

export function listMisProductos(page = 0, sort: OrdenProductos = 'nombre,asc') {
  return list('/api/v1/productos', page, sort);
}

export function listCatalogo(id: string, page = 0, sort: OrdenProductos = 'nombre,asc') {
  return list(`/api/v1/emprendimientos/${encodeURIComponent(id)}/productos`, page, sort);
}

function write(path: string, body: unknown): Promise<Producto> {
  const idempotencyKey = Crypto.randomUUID();
  return withValidSession((session) => requestJson<Producto>(path, {
    method: 'POST',
    headers: jsonHeaders({ accessToken: session.accessToken, idempotencyKey }),
    body: JSON.stringify(body),
  }));
}

export function createProducto(input: CrearProductoInput) {
  return write('/api/v1/productos', input);
}

export function updateInventario(id: string, cantidadDisponible: number) {
  return write(`/api/v1/productos/${encodeURIComponent(id)}/inventario`, { cantidadDisponible });
}

export type EditarProductoInput = Partial<Pick<CrearProductoInput, 'nombre' | 'precio' | 'descripcion'>>;

export function getMiProducto(id: string): Promise<Producto> {
  return withValidSession((session) => requestJson<Producto>(`/api/v1/productos/${encodeURIComponent(id)}`, {
    method: 'GET', headers: jsonHeaders({ accessToken: session.accessToken }),
  }));
}

export function editProducto(id: string, input: EditarProductoInput, version: number): Promise<Producto> {
  return withValidSession((session) => requestJson<Producto>(`/api/v1/productos/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    headers: { ...jsonHeaders({ accessToken: session.accessToken }), 'If-Match': `"${version}"` },
    body: JSON.stringify(input),
  }));
}

export function deleteProducto(id: string): Promise<void> {
  const idempotencyKey = Crypto.randomUUID();
  return withValidSession((session) => requestVoid(`/api/v1/productos/${encodeURIComponent(id)}/eliminar`, {
    method: 'POST', headers: jsonHeaders({ accessToken: session.accessToken, idempotencyKey }),
    body: JSON.stringify({ confirmar: true }),
  }));
}

export function parsePrecio(value: string): number {
  const amount = Number(value.trim());
  if (!/^\d+$/.test(value.trim()) || !Number.isSafeInteger(amount) || amount <= 0) {
    throw new Error('El precio debe ser un entero positivo en pesos, sin puntos ni comas.');
  }
  return amount;
}

export function parseCantidad(value: string, initial = false): number {
  const text = value.trim().replace(',', '.');
  const quantity = Number(text);
  if (!/^\d+(\.\d{1,2})?$/.test(text) || !Number.isFinite(quantity)
      || quantity < 0 || quantity > 99999999.99 || (initial && quantity === 0)) {
    throw new Error(initial
      ? 'La cantidad inicial debe ser mayor que cero y tener hasta dos decimales.'
      : 'La cantidad debe ser cero o mayor y tener hasta dos decimales.');
  }
  return quantity;
}

export async function productErrorMessage(cause: unknown): Promise<string> {
  if (cause instanceof ApiError && cause.status === 401) {
    await clearSession();
    router.replace('/login');
    return 'Tu sesión terminó. Inicia sesión de nuevo.';
  }
  return cause instanceof Error ? cause.message : 'No se pudo completar la operación.';
}
