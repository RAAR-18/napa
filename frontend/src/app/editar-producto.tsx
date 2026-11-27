import { useEffect, useRef, useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, Text } from 'react-native';

import { AuthPage, ErrorBanner, Field, HelperText, PageHeading, PrimaryButton, TextButton, palette } from '../components/AuthUI';
import { ApiError } from '../lib/api';
import { outbox, type AccionPendiente } from '../database/outbox';
import { getSessionGeneration, getValidSession, readSession } from '../lib/auth';
import { leerProductoInventario } from '../lib/inventario-cache';
import { edicionDescartableSinRevision, cambioPendienteProducto, rutaEdicionProducto } from '../lib/productos-pendientes';
import { randomUUID } from '../lib/uuid';
import { editProducto, getMiProducto, parseCantidad, parsePrecio, productErrorMessage, unidadesProducto, type Producto } from '../lib/productos';

export default function EditarProductoScreen() {
  const params = useLocalSearchParams<{ productoId?: string | string[]; pendienteId?: string }>();
  const id = typeof params.productoId === 'string' ? params.productoId : undefined;
  return <ProductoEditor key={params.pendienteId ?? id ?? ''} id={id} pendienteId={params.pendienteId} />;
}

function ProductoEditor({ id, pendienteId }: { id?: string; pendienteId?: string }) {
  const [rechazada, setRechazada] = useState<AccionPendiente | null>(null);
  const [sinAcceso, setSinAcceso] = useState(false);
  const guardando = useRef(false);
  const sesionEditor = useRef<{ generacion: number; cuenta?: string } | null>(null);
  const [producto, setProducto] = useState<Producto | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [revision, setRevision] = useState(0);
  const [nombre, setNombre] = useState('');
  const [precio, setPrecio] = useState('');
  const [descripcion, setDescripcion] = useState('');
  const [cantidad, setCantidad] = useState('');
  const revisandoInventario = rechazada ? cambioPendienteProducto(rechazada)?.inventario === true : false;

  useEffect(() => {
    let active = true;
    const generation = getSessionGeneration();
    const vigente = () => active && generation === getSessionGeneration();
    async function load() {
      try {
        if (!id) throw new Error('Selecciona un producto desde Mis productos.');
        const local = await readSession();
        const session = local?.usuarioId && !pendienteId ? local : await getValidSession();
        if (session.rol !== 'VENDEDOR_AMBULANTE' && session.rol !== 'VENDEDOR_PUNTO_FIJO') {
          throw new Error('Solo los vendedores pueden editar productos.');
        }
        const acciones = session.usuarioId ? await outbox.listar() : [];
        const existente = acciones.find((accion) => cambioPendienteProducto(accion)?.productoId === id);
        if (pendienteId && (!existente || existente.id !== pendienteId || existente.status !== 'FALLIDA'
            || edicionDescartableSinRevision(existente))) {
          throw new Error('Esta edición ya no está disponible para revisar.');
        }
        if (!pendienteId && existente) throw new Error('Este producto ya tiene una edición pendiente.');

        if (vigente() && existente) setRechazada(existente);
        const cached = session.usuarioId && !pendienteId && revision === 0
          ? await leerProductoInventario(session.usuarioId, id)
          : null;
        const current = cached ?? await getMiProducto(id);
        if (!vigente()) return;
        const cambios = existente ? cambioPendienteProducto(existente)?.cambios : undefined;
        sesionEditor.current = { generacion: generation, cuenta: session.usuarioId };
        setRechazada(existente ?? null);
        setProducto(current);
        setNombre(cambios?.nombre ?? current.nombre);
        setPrecio(String(cambios?.precio ?? current.precio));
        setCantidad(String(cambios?.cantidadDisponible ?? current.cantidadDisponible).replace('.', ','));
        setDescripcion((cambios?.descripcion === undefined ? current.descripcion : cambios.descripcion) ?? '');
      } catch (cause) {
        if (vigente()) {
          if (pendienteId && cause instanceof ApiError &&
              (cause.status === 403 || cause.status === 404 || cause.details.estadoActual === 'ELIMINADO')) {
            setSinAcceso(true);
          }
          const message = await productErrorMessage(cause);
          if (active) setError(message);
        }
      } finally { if (active) setLoading(false); }
    }
    void load();
    return () => { active = false; };
  }, [id, pendienteId, revision]);

  function reload() {
    setLoading(true);
    setError(null);
    setProducto(null);
    setRevision((value) => value + 1);
  }

  async function save() {
    if (!producto || guardando.current) return;
    guardando.current = true;
    const generation = getSessionGeneration();
    setError(null);
    try {
      setBusy(true);
      const cuenta = (await readSession())?.usuarioId;
      if (getSessionGeneration() !== generation) return;
      if (sesionEditor.current?.generacion !== generation || sesionEditor.current.cuenta !== cuenta) {
        throw new Error('La cuenta activa cambió. Abre el producto de nuevo.');
      }
      if (revisandoInventario && rechazada) {
        await outbox.corregirFallida(rechazada, {
          body: { cantidadDisponible: parseCantidad(cantidad) },
          ifMatch: `"${producto.version}"`, idempotencyKey: randomUUID(), descripcion: rechazada.descripcion,
        });
        void outbox.procesar().catch(() => undefined);
        if (getSessionGeneration() === generation) router.replace('/mis-productos');
        return;
      }
      if (!nombre.trim()) throw new Error('Escribe el nombre del producto.');
      const valores = { nombre: nombre.trim(), precio: parsePrecio(precio), descripcion: descripcion.trim() || null };
      if (cuenta) {
        const cambios = {
          ...(valores.nombre !== producto.nombre ? { nombre: valores.nombre } : {}),
          ...(valores.precio !== producto.precio ? { precio: valores.precio } : {}),
          ...(valores.descripcion !== producto.descripcion ? { descripcion: valores.descripcion } : {}),
        };
        if (Object.keys(cambios).length === 0) {
          if (rechazada) throw new Error('Los valores coinciden con el servidor. Descarta la propuesta desde Mis productos.');
          router.replace('/mis-productos');
          return;
        }
        const envio = {
          body: cambios, ifMatch: `"${producto.version}"`, idempotencyKey: randomUUID(),
          descripcion: `Editar producto: ${producto.nombre}`,
        };
        if (rechazada) {
          await outbox.corregirFallida(rechazada, envio);
        } else {
          await outbox.encolar({ ...envio, usuarioId: cuenta, method: 'PATCH', path: rutaEdicionProducto(producto.id) });
        }
        void outbox.procesar().catch(() => undefined);
      } else {
        if (rechazada) throw new Error('Inicia sesión para reenviar la edición.');
        await editProducto(producto.id, valores, producto.version);
      }
      if (getSessionGeneration() === generation) router.replace('/mis-productos');
    } catch (cause) { setError(await productErrorMessage(cause)); }
    finally { guardando.current = false; setBusy(false); }
  }

  return (
    <AuthPage>
      <PageHeading eyebrow="Tu catálogo" title={revisandoInventario ? 'Revisar inventario' : 'Editar producto'}
        description={revisandoInventario ? 'Compara la cantidad actual con tu propuesta.' : 'Actualiza el nombre, el precio o la descripción que verán tus clientes.'} />
      <ErrorBanner message={error} />
      {loading ? <ActivityIndicator accessibilityLabel="Cargando producto" color={palette.green} /> : null}
      {producto && revisandoInventario ? (
        <>
          <HelperText>Cantidad actual del servidor: {producto.cantidadDisponible.toLocaleString('es-CO')}</HelperText>
          <Field label="Cantidad propuesta" value={cantidad} onChangeText={setCantidad} editable={!busy}
            keyboardType="decimal-pad" maxLength={12} />
          <PrimaryButton title="Confirmar nuevo ajuste" busy={busy} onPress={() => void save()} />
        </>
      ) : producto ? (
        <>
          {rechazada ? (
            <HelperText>
              Valores actuales del servidor: {producto.nombre} · {producto.precio} COP · {producto.descripcion ?? 'Sin descripción'}.
              Revisa tu propuesta antes de reenviarla.
            </HelperText>
          ) : null}
          <Field label="Nombre del producto" value={nombre} onChangeText={setNombre} editable={!busy} maxLength={100} />
          <Field label="Precio por unidad (COP)" value={precio} onChangeText={setPrecio} editable={!busy} keyboardType="number-pad" maxLength={16} />
          <HelperText>Unidad de venta: {unidadesProducto.find((item) => item.value === producto.unidad)?.label ?? producto.unidad}. Escribe pesos completos, como 3500.</HelperText>
          <Field label="Descripción (opcional)" value={descripcion} onChangeText={setDescripcion} editable={!busy} multiline maxLength={500} />
          <Text style={{ color: palette.muted, marginBottom: 16 }}>La cantidad disponible se actualiza desde Mis productos.</Text>
          <PrimaryButton title={rechazada ? 'Confirmar y reenviar' : 'Guardar cambios'} busy={busy} onPress={() => void save()} />
        </>
      ) : null}
      {sinAcceso && rechazada ? (
        <TextButton variant="danger" icon="x-circle" title="Descartar edición" onPress={() => {
          void outbox.descartar(rechazada.id).then(() => router.replace('/mis-productos'))
            .catch((cause: unknown) => setError(cause instanceof Error ? cause.message : 'No se pudo descartar la edición.'));
        }} />
      ) : null}
      {error && !sinAcceso ? <TextButton icon="refresh-cw" title="Recargar datos del producto" disabled={busy || loading} onPress={reload} /> : null}
    </AuthPage>
  );
}
