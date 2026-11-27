import { useEffect, useRef, useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, Text, View } from 'react-native';

import { AuthPage, ErrorBanner, Field, HelperText, OptionButton, PageHeading, PrimaryButton, palette } from '../components/AuthUI';
import { productStyles } from '../components/ProductosUI';
import { outbox, type AccionPendiente } from '../database/outbox';
import { NetworkError } from '../lib/api';
import { getSessionGeneration, getValidSession, readSession } from '../lib/auth';
import { productoPendiente, RUTA_REGISTRO_PRODUCTO } from '../lib/productos-pendientes';
import { randomUUID } from '../lib/uuid';
import { createProducto, parseCantidad, parsePrecio, productErrorMessage, unidadesProducto, type UnidadProducto } from '../lib/productos';

export default function NuevoProductoScreen() {
  const params = useLocalSearchParams<{ emprendimientoId?: string; pendienteId?: string }>();
  const destino = typeof params.emprendimientoId === 'string' ? params.emprendimientoId : undefined;
  const pendienteId = typeof params.pendienteId === 'string' ? params.pendienteId : undefined;
  const [rechazada, setRechazada] = useState<AccionPendiente | null>(null);
  const enviando = useRef(false);
  const [authorized, setAuthorized] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [nombre, setNombre] = useState('');
  const [precio, setPrecio] = useState('');
  const [unidad, setUnidad] = useState<UnidadProducto>('UNIDAD');
  const [descripcion, setDescripcion] = useState('');
  const [cantidad, setCantidad] = useState('');

  useEffect(() => {
    let active = true;
    async function obtenerSesion() {
      if (destino || pendienteId) {
        const local = await readSession();
        if (local?.usuarioId) return local;
      }
      return getValidSession();
    }
    obtenerSesion().then(async (session) => {
      if (!active) return;
      if (session.rol !== 'VENDEDOR_AMBULANTE' && session.rol !== 'VENDEDOR_PUNTO_FIJO') {
        throw new Error('Solo los vendedores pueden publicar productos.');
      }
      if (pendienteId) {
        const accion = (await outbox.listar()).find((item) => item.id === pendienteId && item.status === 'FALLIDA');
        const datos = accion ? productoPendiente(accion) : null;
        if (!active) return;
        if (!accion || !datos) throw new Error('Este registro ya no está disponible para corregir.');
        setRechazada(accion);
        setNombre(datos.nombre);
        setPrecio(String(datos.precio));
        setUnidad(datos.unidad);
        setDescripcion(datos.descripcion ?? '');
        setCantidad(String(datos.cantidadDisponible));
      }
      setAuthorized(true);
    }).catch(async (cause) => {
      if (active) {
        const message = await productErrorMessage(cause);
        if (active) setError(message);
      }
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [destino, pendienteId]);

  async function submit() {
    if (enviando.current || !authorized) return;
    enviando.current = true;
    setBusy(true);
    setError(null);
    const generation = getSessionGeneration();
    try {
      if (!nombre.trim()) throw new Error('Escribe el nombre del producto.');
      const amount = parsePrecio(precio);
      const quantity = parseCantidad(cantidad, true);
      const origen = rechazada ? productoPendiente(rechazada)?.emprendimientoId : destino;
      const body = {
        nombre: nombre.trim(), precio: amount, unidad,
        descripcion: descripcion.trim() || null, cantidadDisponible: quantity,
        ...(origen ? { emprendimientoId: origen } : {}),
      };
      const key = randomUUID();
      const descripcionEnvio = `Registrar producto: ${body.nombre}`;
      if (rechazada) {
        await outbox.corregirFallida(rechazada, { body, idempotencyKey: key, descripcion: descripcionEnvio });
        void outbox.procesar().catch(() => undefined);
      } else {
        const propietario = (await readSession())?.usuarioId;
        if (getSessionGeneration() !== generation) return;
        if (origen && propietario) {
          await outbox.encolar({
            usuarioId: propietario, method: 'POST', path: RUTA_REGISTRO_PRODUCTO, body,
            idempotencyKey: key, descripcion: descripcionEnvio,
          });
          void outbox.procesar().catch(() => undefined);
        } else {
          try {
            await createProducto(body, key, propietario);
          } catch (cause) {
            if (!(cause instanceof NetworkError)) throw cause;
            throw new Error('Conéctate y abre Mis productos para identificar tu emprendimiento antes de guardar.');
          }
        }
      }
      if (getSessionGeneration() === generation) router.replace('/mis-productos');
    } catch (cause) {
      if (getSessionGeneration() === generation) setError(await productErrorMessage(cause));
    } finally {
      enviando.current = false;
      setBusy(false);
    }
  }

  return (
    <AuthPage>
      <PageHeading eyebrow="Tu catálogo" title={pendienteId ? 'Corregir producto' : 'Nuevo producto'} description="Indica el precio por unidad y la cantidad inicial." />
      <ErrorBanner message={error} />
      {loading ? <ActivityIndicator color={palette.green} /> : null}
      {authorized ? (
        <>
          {rechazada?.lastError ? <HelperText>{rechazada.lastError}</HelperText> : null}
          <Field label="Nombre del producto" value={nombre} onChangeText={setNombre} maxLength={100} editable={!busy} />
          <Field label="Precio por unidad (COP)" value={precio} onChangeText={setPrecio} keyboardType="number-pad" maxLength={16} editable={!busy} />
          <HelperText>Escribe el precio en pesos completos. Por ejemplo: 3500.</HelperText>
          <Text style={productStyles.detail}>Unidad de venta</Text>
          <View style={productStyles.options}>
            {unidadesProducto.map((item) => <OptionButton key={item.value} label={item.label}
              selected={unidad === item.value} onPress={() => { if (!busy) setUnidad(item.value); }} />)}
          </View>
          <Field label="Descripción (opcional)" value={descripcion} onChangeText={setDescripcion} multiline maxLength={500} editable={!busy} />
          <Field label="Cantidad disponible" value={cantidad} onChangeText={setCantidad} keyboardType="decimal-pad" maxLength={12} editable={!busy} />
          <HelperText>La cantidad inicial debe ser mayor que cero. Puedes usar hasta dos decimales, como 2,5 kg.</HelperText>
          <PrimaryButton title={pendienteId ? 'Corregir y reenviar' : 'Publicar producto'} busy={busy} onPress={() => void submit()} />
        </>
      ) : null}
    </AuthPage>
  );
}
