import { useState } from 'react';
import { router } from 'expo-router';
import { StyleSheet, Text, View } from 'react-native';

import { ErrorBanner, Field, HelperText, OptionButton, PrimaryButton, TextButton, palette } from './AuthUI';
import {
  deleteProducto, parseCantidad, productErrorMessage, unidadesProducto, updateInventario,
  type OrdenProductos, type Producto, type ProductosPage,
} from '../lib/productos';

export function ProductoCard({ producto, inventory = false, onUpdated }: {
  producto: Producto; inventory?: boolean; onUpdated?: () => void;
}) {
  const [quantity, setQuantity] = useState(String(producto.cantidadDisponible).replace('.', ','));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const unit = unidadesProducto.find((item) => item.value === producto.unidad)?.label ?? producto.unidad;

  async function update(exhausted = false) {
    if (busy) return;
    setError(null);
    try {
      const value = exhausted ? 0 : parseCantidad(quantity);
      setBusy(true);
      await updateInventario(producto.id, value);
      onUpdated?.();
    } catch (cause) {
      setError(await productErrorMessage(cause));
    } finally { setBusy(false); }
  }

  async function remove() {
    if (busy || !confirmingDelete) return;
    setBusy(true);
    setError(null);
    try {
      await deleteProducto(producto.id);
      onUpdated?.();
    } catch (cause) {
      setError(await productErrorMessage(cause));
    } finally { setBusy(false); }
  }

  return (
    <View style={productStyles.card}>
      <Text accessibilityRole="header" style={productStyles.name}>{producto.nombre}</Text>
      <Text style={productStyles.price}>${producto.precio.toLocaleString('es-CO')} COP / {unit.toLowerCase()}</Text>
      {producto.descripcion ? <Text style={productStyles.detail}>{producto.descripcion}</Text> : null}
      <Text style={productStyles.detail}>Cantidad disponible: {producto.cantidadDisponible.toLocaleString('es-CO')} · {unit}</Text>
      <Text style={[productStyles.status, producto.soloReserva && productStyles.exhausted]}>
        {producto.soloReserva ? (inventory ? 'Agotado · Solo reserva' : 'Solo reserva') : 'Disponible'}
      </Text>
      {inventory ? (
        <>
          <ErrorBanner message={error} />
          {confirmingDelete ? (
            <>
              <Text accessibilityRole="alert" style={productStyles.detail}>
                ¿Eliminar «{producto.nombre}»? Dejará de aparecer en tu catálogo y en el de los clientes.
              </Text>
              <PrimaryButton title={`Confirmar eliminación de ${producto.nombre}`} busy={busy} onPress={() => void remove()} />
              <TextButton title={`Cancelar eliminación de ${producto.nombre}`} disabled={busy}
                onPress={() => { setConfirmingDelete(false); setError(null); }} />
            </>
          ) : (
            <>
          <TextButton title={`Editar ${producto.nombre}`} disabled={busy} onPress={() => router.push({
            pathname: '/editar-producto', params: { productoId: producto.id },
          })} />
          <Field label={`Nueva cantidad de ${producto.nombre}`} value={quantity} onChangeText={setQuantity}
            keyboardType="decimal-pad" editable={!busy} maxLength={12} />
          <PrimaryButton title={`Guardar inventario de ${producto.nombre}`} busy={busy} onPress={() => void update()} />
          {!producto.soloReserva ? <TextButton title={`Marcar ${producto.nombre} como agotado`}
            disabled={busy} onPress={() => void update(true)} /> : null}
          <TextButton title={`Eliminar ${producto.nombre}`} disabled={busy}
            onPress={() => { setConfirmingDelete(true); setError(null); }} />
            </>
          )}
        </>
      ) : null}
    </View>
  );
}

export function OrdenProductosControl({ value, onChange }: { value: OrdenProductos; onChange: (value: OrdenProductos) => void }) {
  return (
    <View style={productStyles.options}>
      <OptionButton label="Nombre A–Z" selected={value === 'nombre,asc'} onPress={() => onChange('nombre,asc')} />
      <OptionButton label="Menor precio" selected={value === 'precio,asc'} onPress={() => onChange('precio,asc')} />
      <OptionButton label="Mayor precio" selected={value === 'precio,desc'} onPress={() => onChange('precio,desc')} />
    </View>
  );
}

export function ProductosPagination({ data, busy, onPage }: { data: ProductosPage; busy: boolean; onPage: (value: number) => void }) {
  if (data.totalElements === 0) return null;
  const pages = Math.ceil(data.totalElements / data.size);
  return (
    <View style={productStyles.pagination}>
      <TextButton title="Página anterior" disabled={busy || data.page === 0} onPress={() => onPage(data.page - 1)} />
      <HelperText>{data.page + 1} de {pages}</HelperText>
      <TextButton title="Página siguiente" disabled={busy || data.page + 1 >= pages} onPress={() => onPage(data.page + 1)} />
    </View>
  );
}

export const productStyles = StyleSheet.create({
  card: { backgroundColor: palette.white, borderColor: palette.border, borderWidth: 1, borderRadius: 16, padding: 18, marginVertical: 8 },
  name: { color: palette.dark, fontSize: 21, fontWeight: '800', marginBottom: 6 },
  price: { color: palette.green, fontSize: 17, fontWeight: '700', marginBottom: 8 },
  detail: { color: palette.muted, fontSize: 14, lineHeight: 21, marginBottom: 8 },
  status: { color: palette.green, fontWeight: '700', marginBottom: 16 },
  exhausted: { color: '#86510F' },
  options: { flexDirection: 'row', flexWrap: 'wrap', marginVertical: 12 },
  pagination: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', marginTop: 16 },
});
