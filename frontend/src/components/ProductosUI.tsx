import { art, fonts } from './theme';
import { useRef, useState } from 'react';
import { router } from 'expo-router';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';

import {
  ErrorBanner,
  Field,
  HelperText,
  OptionButton,
  PrimaryButton,
  TextButton,
  palette,
} from './AuthUI';
import { textoPromedio, type ResumenCalificaciones } from '../lib/calificaciones';
import { Icon, Illustration, StatusBadge, type IconName } from './NapaUI';
import { outbox } from '../database/outbox';
import { getSessionGeneration, readSession } from '../lib/auth';
import { rutaAjusteInventario } from '../lib/productos-pendientes';
import { randomUUID } from '../lib/uuid';
import {
  deleteProducto,
  parseCantidad,
  productErrorMessage,
  unidadesProducto,
  updateInventario,
  type OrdenProductos,
  type Producto,
  type ProductosPage,
} from '../lib/productos';

function ProductAction({ title, label, icon, tone, disabled, busy = false, onPress }: {
  title: string;
  label: string;
  icon: IconName;
  tone: 'edit' | 'save' | 'exhausted' | 'delete';
  disabled?: boolean;
  busy?: boolean;
  onPress: () => void;
}) {
  const colors = {
    edit: { backgroundColor: '#FFF0E5', borderColor: '#F1B69D', color: palette.coralDark },
    save: { backgroundColor: '#EEF3E4', borderColor: '#C8D6B0', color: '#496538' },
    exhausted: { backgroundColor: '#FFF5DC', borderColor: '#EAD39B', color: '#86510F' },
    delete: { backgroundColor: '#FFF0EC', borderColor: '#EBC1B7', color: palette.error },
  }[tone];
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={title}
      accessibilityState={{ busy, disabled: Boolean(disabled || busy) }}
      disabled={disabled || busy}
      onPress={onPress}
      style={({ pressed, hovered }: { pressed: boolean; hovered?: boolean }) => [
        productStyles.action,
        { backgroundColor: colors.backgroundColor, borderColor: colors.borderColor },
        hovered && !disabled && !busy && { borderColor: colors.color },
        pressed && { opacity: 0.8, transform: [{ scale: 0.98 }] },
        (disabled || busy) && { opacity: 0.45 },
      ]}
    >
      <View style={[productStyles.actionIcon, { backgroundColor: `${colors.color}12` }]}>
        {busy ? <ActivityIndicator size="small" color={colors.color} /> : <Icon name={icon} size={20} color={colors.color} />}
      </View>
      <Text style={[productStyles.actionLabel, { color: colors.color }]}>{label}</Text>
      {tone === 'edit' ? <Icon name="chevron-right" size={18} color={colors.color} /> : null}
    </Pressable>
  );
}

export function ProductoCard({
  producto,
  inventory = false,
  edicionBloqueada = false,
  offline = false,
  onUpdated,
  resumen,
}: {
  producto: Producto;
  inventory?: boolean;
  edicionBloqueada?: boolean;
  offline?: boolean;
  onUpdated?: () => void;
  /** RF-02: promedio del producto; ausente si aún no se cargó. */
  resumen?: ResumenCalificaciones;
}) {
  const ajustando = useRef(false);
  const [quantity, setQuantity] = useState(String(producto.cantidadDisponible).replace('.', ','));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const unit =
    unidadesProducto.find((item) => item.value === producto.unidad)?.label ?? producto.unidad;

  async function update(exhausted = false) {
    if (busy || ajustando.current || edicionBloqueada) return;
    ajustando.current = true;
    const generation = getSessionGeneration();
    setError(null);
    try {
      const value = exhausted ? 0 : parseCantidad(quantity);
      setBusy(true);
      const session = await readSession();
      if (generation !== getSessionGeneration()) return;
      if (session?.usuarioId) {
        await outbox.encolar({
          usuarioId: session.usuarioId, method: 'POST', path: rutaAjusteInventario(producto.id),
          body: { cantidadDisponible: value }, ifMatch: `"${producto.version}"`,
          idempotencyKey: randomUUID(), descripcion: `Ajustar inventario: ${producto.nombre}`,
        });
        void outbox.procesar().catch(() => undefined);
      } else {
        if (offline) throw new Error('Inicia sesión para guardar un ajuste sin conexión.');
        await updateInventario(producto.id, value);
        onUpdated?.();
      }
    } catch (cause) {
      setError(await productErrorMessage(cause));
    } finally {
      ajustando.current = false;
      setBusy(false);
    }
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
    } finally {
      setBusy(false);
    }
  }

  return (
    <View style={productStyles.card}>
      <Illustration
        source={
          /pescad|camar[oó]n/i.test(producto.nombre)
            ? art.pescado
            : /arepa|jugo|comida|arroz|empanada/i.test(producto.nombre)
              ? art.comida
              : /mango|banano|fruta|aguacate|fresa|verdura/i.test(producto.nombre)
                ? art.frutas
                : art.paquete
        }
        style={{ height: 130 }}
      />
      <View style={productStyles.body}>
        <Text accessibilityRole="header" style={productStyles.name}>
          {producto.nombre}
        </Text>
        <Text style={productStyles.price}>
          ${producto.precio.toLocaleString('es-CO')} COP / {unit.toLowerCase()}
        </Text>
        {producto.descripcion ? (
          <Text style={productStyles.detail}>{producto.descripcion}</Text>
        ) : null}
        <Text style={productStyles.detail}>
          {offline ? 'Cantidad guardada' : 'Cantidad disponible'}: {producto.cantidadDisponible.toLocaleString('es-CO')} · {unit}
        </Text>
        <View style={{ marginBottom: 14 }}>
          <StatusBadge
            label={
              producto.soloReserva
                ? inventory
                  ? 'Agotado · Solo reserva'
                  : 'Solo reserva'
                : 'Disponible'
            }
            tone={producto.soloReserva ? 'peach' : 'sage'}
          />
        </View>
        {resumen ? (
          <View style={productStyles.rating}>
            <Text
              accessibilityLabel={`Calificación de ${producto.nombre}: ${textoPromedio(resumen)}`}
              style={productStyles.ratingText}
            >
              {resumen.promedio !== null ? '★ ' : ''}
              {textoPromedio(resumen)}
            </Text>
            {resumen.total > 0 ? (
              <TextButton variant="gold" icon="star"
                title={`Ver opiniones de ${producto.nombre}`}
                onPress={() =>
                  router.push({
                    pathname: '/calificaciones',
                    params: {
                      tipoEntidad: 'PRODUCTO',
                      entidadId: producto.id,
                      titulo: producto.nombre,
                    },
                  })
                }
              />
            ) : null}
          </View>
        ) : null}
        {inventory ? (
          <>
            <ErrorBanner message={error} />
            {confirmingDelete ? (
              <>
                <Text accessibilityRole="alert" style={productStyles.detail}>
                  ¿Eliminar «{producto.nombre}»? Dejará de aparecer en tu catálogo y en el de los
                  clientes.
                </Text>
                <PrimaryButton
                  title={`Confirmar eliminación de ${producto.nombre}`}
                  busy={busy}
                  onPress={() => void remove()}
                />
                <TextButton
                  title={`Cancelar eliminación de ${producto.nombre}`}
                  disabled={busy}
                  onPress={() => {
                    setConfirmingDelete(false);
                    setError(null);
                  }}
                />
              </>
            ) : (
              <>
                <ProductAction
                  title={`Editar ${producto.nombre}`}
                  label="Editar producto"
                  icon="edit-3"
                  tone="edit"
                  disabled={busy || edicionBloqueada}
                  onPress={() =>
                    router.push({
                      pathname: '/editar-producto',
                      params: { productoId: producto.id },
                    })
                  }
                />
                <Field
                  label={`Nueva cantidad de ${producto.nombre}`}
                  value={quantity}
                  onChangeText={setQuantity}
                  keyboardType="decimal-pad"
                  editable={!busy && !edicionBloqueada}
                  maxLength={12}
                />
                <ProductAction
                  title={`Guardar inventario de ${producto.nombre}`}
                  label={busy ? 'Guardando inventario…' : 'Guardar inventario'}
                  icon="save"
                  tone="save"
                  busy={busy}
                  disabled={edicionBloqueada}
                  onPress={() => void update()}
                />
                {!producto.soloReserva ? (
                  <ProductAction
                    title={`Marcar ${producto.nombre} como agotado`}
                    label="Marcar como agotado"
                    icon="archive"
                    tone="exhausted"
                    disabled={busy || edicionBloqueada}
                    onPress={() => void update(true)}
                  />
                ) : null}
                {!offline ? (
                  <ProductAction
                    title={`Eliminar ${producto.nombre}`}
                    label="Eliminar producto"
                    icon="trash-2"
                    tone="delete"
                    disabled={busy}
                    onPress={() => {
                      setConfirmingDelete(true);
                      setError(null);
                    }}
                  />
                ) : null}
              </>
            )}
          </>
        ) : null}
      </View>
    </View>
  );
}

export function OrdenProductosControl({
  value,
  onChange,
}: {
  value: OrdenProductos;
  onChange: (value: OrdenProductos) => void;
}) {
  return (
    <View style={productStyles.options}>
      <OptionButton
        label="Nombre A–Z"
        selected={value === 'nombre,asc'}
        onPress={() => onChange('nombre,asc')}
      />
      <OptionButton
        label="Menor precio"
        selected={value === 'precio,asc'}
        onPress={() => onChange('precio,asc')}
      />
      <OptionButton
        label="Mayor precio"
        selected={value === 'precio,desc'}
        onPress={() => onChange('precio,desc')}
      />
    </View>
  );
}

export function ProductosPagination({
  data,
  busy,
  onPage,
}: {
  data: ProductosPage;
  busy: boolean;
  onPage: (value: number) => void;
}) {
  if (data.totalElements === 0) return null;
  const pages = Math.ceil(data.totalElements / data.size);
  return (
    <View style={productStyles.pagination}>
      <TextButton icon="chevron-left"
        title="Página anterior"
        disabled={busy || data.page === 0}
        onPress={() => onPage(data.page - 1)}
      />
      <HelperText>
        {data.page + 1} de {pages}
      </HelperText>
      <TextButton icon="chevron-right"
        title="Página siguiente"
        disabled={busy || data.page + 1 >= pages}
        onPress={() => onPage(data.page + 1)}
      />
    </View>
  );
}

export const productStyles = StyleSheet.create({
  card: {
    backgroundColor: palette.white,
    borderColor: palette.border,
    borderWidth: 1,
    borderRadius: 18,
    overflow: 'hidden',
    flex: 1,
    marginVertical: 8,
  },
  body: { padding: 18 },
  action: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    minHeight: 54,
    paddingVertical: 8,
    paddingHorizontal: 12,
    marginVertical: 6,
    borderWidth: 1,
    borderRadius: 14,
  },
  actionIcon: { width: 34, height: 34, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
  actionLabel: { flex: 1, fontFamily: fonts.sans, fontSize: 14, lineHeight: 20, fontWeight: '800' },
  name: {
    color: palette.dark,
    fontFamily: fonts.display,
    fontSize: 25,
    fontWeight: '700',
    marginBottom: 6,
  },
  price: {
    color: palette.green,
    fontFamily: fonts.sans,
    fontSize: 17,
    fontWeight: '700',
    marginBottom: 8,
  },
  rating: { marginTop: 6 },
  ratingText: { color: '#9A6700', fontFamily: fonts.sans, fontSize: 14, fontWeight: '700' },
  detail: {
    color: palette.muted,
    fontFamily: fonts.sans,
    fontSize: 14,
    lineHeight: 21,
    marginBottom: 8,
  },
  status: { color: palette.green, fontWeight: '700', marginBottom: 16 },
  exhausted: { color: '#86510F' },
  options: { flexDirection: 'row', flexWrap: 'wrap', marginVertical: 12 },
  pagination: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 16,
  },
});
