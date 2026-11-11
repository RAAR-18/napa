import { useEffect, useState } from 'react';
import { router } from 'expo-router';
import { ActivityIndicator, Text, View } from 'react-native';

import { AuthPage, ErrorBanner, Field, HelperText, OptionButton, PageHeading, PrimaryButton, TextButton, palette } from '../components/AuthUI';
import { productStyles } from '../components/ProductosUI';
import { getValidSession } from '../lib/auth';
import { createProducto, parseCantidad, parsePrecio, productErrorMessage, unidadesProducto, type UnidadProducto } from '../lib/productos';

export default function NuevoProductoScreen() {
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
    getValidSession().then((session) => {
      if (!active) return;
      if (session.rol !== 'VENDEDOR_AMBULANTE' && session.rol !== 'VENDEDOR_PUNTO_FIJO') {
        throw new Error('Solo los vendedores pueden publicar productos.');
      }
      setAuthorized(true);
    }).catch(async (cause) => {
      if (active) {
        const message = await productErrorMessage(cause);
        if (active) setError(message);
      }
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  async function submit() {
    if (busy || !authorized) return;
    setError(null);
    try {
      if (!nombre.trim()) throw new Error('Escribe el nombre del producto.');
      const amount = parsePrecio(precio);
      const quantity = parseCantidad(cantidad, true);
      setBusy(true);
      await createProducto({ nombre: nombre.trim(), precio: amount, unidad,
        descripcion: descripcion.trim() || null, cantidadDisponible: quantity });
      router.replace('/mis-productos');
    } catch (cause) { setError(await productErrorMessage(cause)); }
    finally { setBusy(false); }
  }

  return (
    <AuthPage>
      <PageHeading eyebrow="Tu catálogo" title="Nuevo producto" description="Publica un producto con su precio por unidad y la cantidad que tienes disponible." />
      <ErrorBanner message={error} />
      {loading ? <ActivityIndicator color={palette.green} /> : null}
      {authorized ? (
        <>
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
          <PrimaryButton title="Publicar producto" busy={busy} onPress={() => void submit()} />
        </>
      ) : null}
      <TextButton title="Volver a mis productos" disabled={busy} onPress={() => router.replace('/mis-productos')} />
    </AuthPage>
  );
}
