/** F041 (RNF28): acción crítica que espera a ser enviada al servidor. */
export type EstadoEnvio = 'PENDIENTE' | 'FALLIDA';

export type MetodoEnvio = 'POST' | 'PUT' | 'PATCH';

export type AccionPendiente = {
  id: string;
  usuarioId: string;
  method: MetodoEnvio;
  path: string;
  /** JSON ya serializado, o nulo si la acción no lleva cuerpo. */
  body: string | null;
  /** La misma clave en cada reintento: el servidor no aplica la acción dos veces (RNF27). */
  idempotencyKey: string;
  ifMatch?: string;
  errorStatus?: number;
  /** Texto para el usuario, por ejemplo "Calificación de Frutas Ana". */
  descripcion: string;
  status: EstadoEnvio;
  attempts: number;
  lastError: string | null;
  /** Epoch en ms: antes de este momento no se reintenta. */
  nextAttemptAt: number;
  createdAt: number;
};

/**
 * Persistencia de la cola:
 * SQLite en Android/iOS e IndexedDB en web.
 * Las pruebas pueden proporcionar un adaptador de memoria.
 */
export interface OutboxStore {
  insertar(accion: AccionPendiente): Promise<void>;
  /** Todas, de la más antigua a la más reciente. */
  listar(): Promise<AccionPendiente[]>;
  actualizar(accion: AccionPendiente): Promise<void>;
  reemplazarFallida(accion: AccionPendiente, claveAnterior: string): Promise<boolean>;
  borrar(id: string): Promise<void>;
  iniciarPrimerIntento?(id: string): Promise<boolean>;
  borrarSinIntentos?(id: string, usuarioId: string): Promise<boolean>;
  borrarTodo(): Promise<void>;
}
