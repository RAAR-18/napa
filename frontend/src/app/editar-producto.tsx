import { useEffect, useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, Text } from 'react-native';

import { AuthPage, ErrorBanner, Field, HelperText, PageHeading, PrimaryButton, TextButton, palette } from '../components/AuthUI';
import { getValidSession } from '../lib/auth';
import { editProducto, getMiProducto, parsePrecio, productErrorMessage, unidadesProducto, type Producto } from '../lib/productos';

export default function EditarProductoScreen() {
  const params = useLocalSearchParams<{ productoId?: string | string[] }>();
  const id = typeof params.productoId === 'string' ? params.productoId : undefined;
  return <ProductoEditor key={id ?? ''} id={id} />;
}

function ProductoEditor({ id }: { id?: string }) {
  const [producto, setProducto] = useState<Producto | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [revision, setRevision] = useState(0);
  const [nombre, setNombre] = useState('');
  const [precio, setPrecio] = useState('');
  const [descripcion, setDescripcion] = useState('');

  useEffect(() => {
    let active = true;
    async function load() {
      try {
        if (!id) throw new Error('Selecciona un producto desde Mis productos.');
        const session = await getValidSession();
        if (session.rol !== 'VENDEDOR_AMBULANTE' && session.rol !== 'VENDEDOR_PUNTO_FIJO') {
          throw new Error('Solo los vendedores pueden editar productos.');
        }
        const current = await getMiProducto(id);
        if (active) {
          setProducto(current);
          setNombre(current.nombre);
          setPrecio(String(current.precio));
          setDescripcion(current.descripcion ?? '');
        }
      } catch (cause) {
        if (active) {
          const message = await productErrorMessage(cause);
          if (active) setError(message);
        }
      } finally { if (active) setLoading(false); }
    }
    void load();
    return () => { active = false; };
  }, [id, revision]);

  function reload() {
    setLoading(true);
    setError(null);
    setProducto(null);
    setRevision((value) => value + 1);
  }

  async function save() {
    if (!producto || busy) return;
    setError(null);
    try {
      if (!nombre.trim()) throw new Error('Escribe el nombre del producto.');
      const amount = parsePrecio(precio);
      setBusy(true);
      await editProducto(producto.id, {
        nombre: nombre.trim(), precio: amount, descripcion: descripcion.trim() || null,
      }, producto.version);
      router.replace('/mis-productos');
    } catch (cause) { setError(await productErrorMessage(cause)); }
    finally { setBusy(false); }
  }

  return (
    <AuthPage>
      <PageHeading eyebrow="Tu catálogo" title="Editar producto" description="Actualiza el nombre, el precio o la descripción que verán tus clientes." />
      <ErrorBanner message={error} />
      {loading ? <ActivityIndicator accessibilityLabel="Cargando producto" color={palette.green} /> : null}
      {producto ? (
        <>
          <Field label="Nombre del producto" value={nombre} onChangeText={setNombre} editable={!busy} maxLength={100} />
          <Field label="Precio por unidad (COP)" value={precio} onChangeText={setPrecio} editable={!busy} keyboardType="number-pad" maxLength={16} />
          <HelperText>Unidad de venta: {unidadesProducto.find((item) => item.value === producto.unidad)?.label ?? producto.unidad}. Escribe pesos completos, como 3500.</HelperText>
          <Field label="Descripción (opcional)" value={descripcion} onChangeText={setDescripcion} editable={!busy} multiline maxLength={500} />
          <Text style={{ color: palette.muted, marginBottom: 16 }}>La cantidad disponible se actualiza desde Mis productos.</Text>
          <PrimaryButton title="Guardar cambios" busy={busy} onPress={() => void save()} />
        </>
      ) : null}
      {error ? <TextButton title="Recargar datos del producto" disabled={busy || loading} onPress={reload} /> : null}
      <TextButton title="Cancelar y volver a mis productos" disabled={busy} onPress={() => router.replace('/mis-productos')} />
    </AuthPage>
  );
}
