import { useMemo, useRef, useState } from 'react';
import * as Crypto from 'expo-crypto';
import * as Location from 'expo-location';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';

import {
  ErrorBanner,
  Field,
  HelperText,
  OptionButton,
  PrimaryButton,
  TextButton,
  palette,
} from './AuthUI';
import { ProductoCard, ProductosPagination } from './ProductosUI';
import { ApiError } from '../lib/api';
import {
  type CotizacionPedido,
  cotizarPedido,
  crearPedido,
  type CrearPedidoInput,
  type ModalidadPedido,
  type MedioPagoPedido,
  type PedidoCreado,
  type UbicacionEntregaInput,
} from '../lib/pedidos';
import {
  parseCantidad,
  productErrorMessage,
  type Producto,
  type ProductosPage,
} from '../lib/productos';
import type { TipoVendedor } from '../lib/emprendimientos';

type CartItem = {
  producto: Producto;
  cantidad: number;
};

type Props = {
  emprendimientoId: string;
  tipoVendedor: TipoVendedor;
  productos: Producto[];
  enabled: boolean;
  pageData: ProductosPage | null;
  pageBusy: boolean;
  onPage: (page: number) => void;
};

const modalityNames: Record<ModalidadPedido, string> = {
  ENTREGA_DIRECTA: 'Entrega directa',
  RECOGIDA: 'Recogida en el punto',
  DOMICILIO: 'Domicilio',
};

const paymentNames: Record<MedioPagoPedido, string> = {
  EFECTIVO: 'Efectivo',
  CUENTA_BANCARIA: 'Transferencia bancaria',
};

function futureReservation(value: string): string | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(value.trim());
  if (!match) {
    throw new Error('Usa fecha y hora en formato AAAA-MM-DDTHH:mm.');
  }
  const [, yearText, monthText, dayText, hourText, minuteText] = match;
  const date = new Date(
    Number(yearText),
    Number(monthText) - 1,
    Number(dayText),
    Number(hourText),
    Number(minuteText),
  );
  if (
    date.getFullYear() !== Number(yearText) ||
    date.getMonth() !== Number(monthText) - 1 ||
    date.getDate() !== Number(dayText) ||
    date.getHours() !== Number(hourText) ||
    date.getMinutes() !== Number(minuteText) ||
    date.getTime() <= Date.now()
  ) {
    throw new Error('La fecha y hora de reserva deben ser válidas y futuras.');
  }
  return date.toISOString();
}

function deliveryLocation(
  address: string,
  latitude: string,
  longitude: string,
  reference: string,
): UbicacionEntregaInput {
  const lat = Number(latitude.trim().replace(',', '.'));
  const lng = Number(longitude.trim().replace(',', '.'));
  if (
    !address.trim() ||
    address.trim().length > 300 ||
    !Number.isFinite(lat) ||
    lat < -90 ||
    lat > 90 ||
    !Number.isFinite(lng) ||
    lng < -180 ||
    lng > 180 ||
    reference.trim().length > 300
  ) {
    throw new Error(
      'Indica una dirección y coordenadas válidas. La dirección y la referencia admiten hasta 300 caracteres.',
    );
  }
  return {
    latitud: lat,
    longitud: lng,
    direccion: address.trim(),
    referencia: reference.trim() || null,
  };
}

export function PedidoCatalogo({
  emprendimientoId,
  tipoVendedor,
  productos,
  enabled,
  pageData,
  pageBusy,
  onPage,
}: Props) {
  const [cart, setCart] = useState<CartItem[]>([]);
  const [quantities, setQuantities] = useState<Record<string, string>>({});
  const [step, setStep] = useState<'cart' | 'delivery' | 'summary'>('cart');
  const [modalidad, setModalidad] = useState<ModalidadPedido>(
    tipoVendedor === 'AMBULANTE' ? 'ENTREGA_DIRECTA' : 'RECOGIDA',
  );
  const [availableModalities, setAvailableModalities] =
    useState<ModalidadPedido[] | null>(null);
  const [address, setAddress] = useState('');
  const [latitude, setLatitude] = useState('');
  const [longitude, setLongitude] = useState('');
  const [reference, setReference] = useState('');
  const [reserve, setReserve] = useState(false);
  const [reservationDate, setReservationDate] = useState('');
  const [quote, setQuote] = useState<CotizacionPedido | null>(null);
  const [paymentMethod, setPaymentMethod] = useState<MedioPagoPedido | null>(
    null,
  );
  const [created, setCreated] = useState<PedidoCreado | null>(null);
  const [quoting, setQuoting] = useState(false);
  const [creating, setCreating] = useState(false);
  const [locationBusy, setLocationBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const pendingRequest = useRef<{ body: string; key: string } | null>(null);

  const modalities = useMemo(() => {
    const options: ModalidadPedido[] =
      tipoVendedor === 'AMBULANTE'
        ? ['ENTREGA_DIRECTA']
        : ['RECOGIDA', 'DOMICILIO'];
    return availableModalities
      ? options.filter((option) => availableModalities.includes(option))
      : options;
  }, [availableModalities, tipoVendedor]);

  const estimatedSubtotal = cart.reduce(
    (total, item) => total + Math.round(item.producto.precio * item.cantidad),
    0,
  );

  function invalidateQuote() {
    setQuote(null);
    setPaymentMethod(null);
    setError(null);
  }

  function addProduct(producto: Producto) {
    setError(null);
    let cantidad: number;
    try {
      cantidad = parseCantidad(quantities[producto.id] ?? '1');
      if (cantidad <= 0 || cantidad > 20) {
        throw new Error('Cada producto debe tener una cantidad entre 0 y 20.');
      }
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : 'Indica una cantidad válida para el producto.',
      );
      return;
    }

    const exists = cart.some((item) => item.producto.id === producto.id);
    if (!exists && cart.length >= 20) {
      setError('Un pedido puede incluir como máximo 20 productos distintos.');
      return;
    }
    setCart((current) => [
      ...current.filter((item) => item.producto.id !== producto.id),
      { producto, cantidad },
    ]);
    invalidateQuote();
  }

  function removeProduct(productId: string) {
    setCart((current) =>
      current.filter((item) => item.producto.id !== productId),
    );
    invalidateQuote();
  }

  function continueToDelivery() {
    setError(null);
    if (cart.length === 0) {
      setError('Agrega al menos un producto para continuar.');
      return;
    }
    setStep('delivery');
  }

  function quoteInput(): Parameters<typeof cotizarPedido>[0] {
    if (!emprendimientoId) {
      throw new Error('No se pudo identificar el emprendimiento.');
    }
    const input: Parameters<typeof cotizarPedido>[0] = {
      emprendimientoId,
      modalidad,
      items: cart.map(({ producto, cantidad }) => ({
        productoId: producto.id,
        cantidad,
      })),
    };
    if (modalidad !== 'RECOGIDA') {
      input.ubicacionEntrega = deliveryLocation(
        address,
        latitude,
        longitude,
        reference,
      );
    }
    if (reserve) {
      const parsed = futureReservation(reservationDate);
      if (parsed) input.reserva = parsed;
    }
    return input;
  }

  async function reviewQuote() {
    setError(null);
    setQuoting(true);
    try {
      const result = await cotizarPedido(quoteInput());
      setAvailableModalities(result.modalidadesDisponibles);
      setQuote(result);
      setPaymentMethod((current) =>
        current && result.mediosPagoDisponibles.includes(current)
          ? current
          : (result.mediosPagoDisponibles[0] ?? null),
      );
      setStep('summary');
    } catch (cause) {
      if (
        cause instanceof ApiError &&
        cause.code === 'BANK_ACCOUNT_REQUIRED'
      ) {
        setAvailableModalities(['RECOGIDA']);
        setModalidad('RECOGIDA');
      }
      setError(await productErrorMessage(cause));
    } finally {
      setQuoting(false);
    }
  }

  function createInput(): CrearPedidoInput {
    if (!quote || !paymentMethod) {
      throw new Error('Elige un medio de pago disponible antes de confirmar.');
    }
    return {
      ...quoteInput(),
      medioPago: paymentMethod,
      totalEsperado: quote.total,
    };
  }

  async function confirmOrder() {
    setError(null);
    if (!quote || !paymentMethod || quote.mediosPagoDisponibles.length === 0) {
      setError('Este vendedor aún no tiene un medio de pago disponible.');
      return;
    }
    setCreating(true);
    try {
      const input = createInput();
      const body = JSON.stringify(input);
      if (!pendingRequest.current || pendingRequest.current.body !== body) {
        pendingRequest.current = { body, key: Crypto.randomUUID() };
      }
      const order = await crearPedido(input, pendingRequest.current.key);
      pendingRequest.current = null;
      setCreated(order);
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 409) {
        setQuote(null);
        setPaymentMethod(null);
        setStep('delivery');
      }
      setError(await productErrorMessage(cause));
    } finally {
      setCreating(false);
    }
  }

  async function locateDevice() {
    setError(null);
    setLocationBusy(true);
    try {
      const permission = await Location.requestForegroundPermissionsAsync();
      if (permission.status !== 'granted') {
        setError(
          'No se concedió permiso de ubicación. Puedes escribir las coordenadas manualmente.',
        );
        return;
      }
      const location = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.Balanced,
      });
      setLatitude(String(location.coords.latitude));
      setLongitude(String(location.coords.longitude));
      invalidateQuote();
    } catch (cause) {
      setError(
        cause instanceof Error
          ? `No se pudo obtener tu ubicación: ${cause.message}`
          : 'No se pudo obtener tu ubicación. Puedes ingresar las coordenadas manualmente.',
      );
    } finally {
      setLocationBusy(false);
    }
  }

  if (!enabled) return null;

  if (created) {
    return (
      <View style={styles.section}>
        <Text accessibilityRole="header" style={styles.heading}>
          Pedido enviado
        </Text>
        <Text style={styles.detail}>Estado: {created.estado}</Text>
        <Text style={styles.total}>
          Total: ${created.total.toLocaleString('es-CO')} COP
        </Text>
        <HelperText>
          Guarda este identificador para consultar tu pedido: {created.id}
        </HelperText>
      </View>
    );
  }

  return (
    <View style={styles.section}>
      <Text accessibilityRole="header" style={styles.heading}>
        Arma tu pedido
      </Text>
      {step === 'cart' ? (
        <>
          <HelperText>
            Puedes agregar hasta 20 productos. Las cantidades y la disponibilidad
            se validarán nuevamente antes de confirmar.
          </HelperText>
          {productos.map((producto) => (
            <View key={producto.id} style={styles.product}>
              <ProductoCard producto={producto} />
              <Field
                label={`Cantidad de ${producto.nombre}`}
                keyboardType="decimal-pad"
                value={quantities[producto.id] ?? '1'}
                onChangeText={(value) =>
                  setQuantities((current) => ({
                    ...current,
                    [producto.id]: value,
                  }))
                }
              />
              <TextButton
                title={`Agregar ${producto.nombre} al pedido`}
                onPress={() => addProduct(producto)}
              />
            </View>
          ))}
          {pageData ? (
            <ProductosPagination
              data={pageData}
              busy={pageBusy}
              onPage={onPage}
            />
          ) : null}
          {cart.length > 0 ? (
            <View style={styles.card}>
              <Text style={styles.subheading}>Tu selección</Text>
              {cart.map(({ producto, cantidad }) => (
                <View key={producto.id} style={styles.cartRow}>
                  <Text style={styles.detail}>
                    {cantidad.toLocaleString('es-CO')} · {producto.nombre}
                  </Text>
                  <TextButton
                    title={`Quitar ${producto.nombre}`}
                    onPress={() => removeProduct(producto.id)}
                  />
                </View>
              ))}
              <Text style={styles.total}>
                Subtotal estimado: ${estimatedSubtotal.toLocaleString('es-CO')} COP
              </Text>
            </View>
          ) : (
            <HelperText>Aún no has agregado productos.</HelperText>
          )}
          <PrimaryButton
            title="Continuar con la entrega"
            onPress={continueToDelivery}
          />
        </>
      ) : null}

      {step === 'delivery' ? (
        <>
          <Text style={styles.subheading}>Modalidad de entrega</Text>
          <View style={styles.options}>
            {modalities.map((option) => (
              <OptionButton
                key={option}
                label={modalityNames[option]}
                selected={modalidad === option}
                onPress={() => {
                  setModalidad(option);
                  invalidateQuote();
                }}
              />
            ))}
          </View>
          {modalidad !== 'RECOGIDA' ? (
            <>
              <Text style={styles.subheading}>Ubicación de entrega</Text>
              <HelperText>
                La ubicación solo se enviará al vendedor al cotizar una entrega.
              </HelperText>
              <Field
                label="Dirección de entrega"
                maxLength={300}
                value={address}
                onChangeText={(value) => {
                  setAddress(value);
                  invalidateQuote();
                }}
              />
              <View style={styles.coordinates}>
                <View style={styles.coordinate}>
                  <Field
                    label="Latitud"
                    keyboardType="decimal-pad"
                    value={latitude}
                    onChangeText={(value) => {
                      setLatitude(value);
                      invalidateQuote();
                    }}
                  />
                </View>
                <View style={styles.coordinate}>
                  <Field
                    label="Longitud"
                    keyboardType="decimal-pad"
                    value={longitude}
                    onChangeText={(value) => {
                      setLongitude(value);
                      invalidateQuote();
                    }}
                  />
                </View>
              </View>
              <Field
                label="Referencia (opcional)"
                maxLength={300}
                value={reference}
                onChangeText={(value) => {
                  setReference(value);
                  invalidateQuote();
                }}
              />
              <PrimaryButton
                title="Usar mi ubicación actual"
                busy={locationBusy}
                onPress={() => void locateDevice()}
              />
            </>
          ) : (
            <HelperText>No necesitas indicar una dirección para recoger.</HelperText>
          )}

          <Text style={styles.subheading}>¿Es una reserva?</Text>
          <View style={styles.options}>
            <OptionButton
              label="Pedido inmediato"
              selected={!reserve}
              onPress={() => {
                setReserve(false);
                invalidateQuote();
              }}
            />
            <OptionButton
              label="Reserva"
              selected={reserve}
              onPress={() => {
                setReserve(true);
                invalidateQuote();
              }}
            />
          </View>
          {reserve ? (
            <>
              <Field
                label="Fecha y hora acordadas (AAAA-MM-DDTHH:mm)"
                placeholder="2026-12-24T15:30"
                value={reservationDate}
                onChangeText={(value) => {
                  setReservationDate(value);
                  invalidateQuote();
                }}
              />
              <HelperText>
                Los productos agotados solo se aceptan en reservas; el vendedor
                confirmará si puede cumplirla.
              </HelperText>
            </>
          ) : null}
          <ErrorBanner message={error} />
          <PrimaryButton
            title="Cotizar pedido"
            busy={quoting}
            onPress={() => void reviewQuote()}
          />
          <TextButton
            title="Volver a los productos"
            onPress={() => {
              setStep('cart');
              setError(null);
            }}
          />
        </>
      ) : null}

      {step === 'summary' && quote ? (
        <>
          <Text style={styles.subheading}>Revisa tu pedido</Text>
          {quote.items.map((item) => (
            <View key={item.productoId} style={styles.summaryRow}>
              <Text style={styles.detail}>
                {item.cantidad.toLocaleString('es-CO')} · {item.nombre}
              </Text>
              <Text style={styles.detail}>
                ${item.subtotal.toLocaleString('es-CO')} COP
              </Text>
            </View>
          ))}
          <Text style={styles.detail}>
            Modalidad: {modalityNames[modalidad]}
          </Text>
          <Text style={styles.detail}>
            Productos: ${quote.subtotal.toLocaleString('es-CO')} COP
          </Text>
          {quote.tarifaDomicilio !== null ? (
            <Text style={styles.detail}>
              Domicilio: ${quote.tarifaDomicilio.toLocaleString('es-CO')} COP
            </Text>
          ) : null}
          <Text style={styles.total}>
            Total: ${quote.total.toLocaleString('es-CO')} COP
          </Text>
          <Text style={styles.subheading}>Medio de pago</Text>
          {quote.mediosPagoDisponibles.length > 0 ? (
            <View style={styles.options}>
              {quote.mediosPagoDisponibles.map((option) => (
                <OptionButton
                  key={option}
                  label={paymentNames[option]}
                  selected={paymentMethod === option}
                  onPress={() => setPaymentMethod(option)}
                />
              ))}
            </View>
          ) : (
            <HelperText>
              Este vendedor aún no ha habilitado un medio de pago.
            </HelperText>
          )}
          {quote.pagoDomicilio ? (
            <View style={styles.notice}>
              <Text style={styles.noticeText}>
                El domicilio se paga por transferencia por el total de $
                {quote.pagoDomicilio.total.toLocaleString('es-CO')} COP cuando el
                vendedor acepte el pedido.
              </Text>
            </View>
          ) : null}
          {quote.advertencias.map((warning) => (
            <HelperText key={warning}>{warning}</HelperText>
          ))}
          <ErrorBanner message={error} />
          <PrimaryButton
            title="Confirmar pedido"
            busy={creating}
            disabled={quote.mediosPagoDisponibles.length === 0 || !paymentMethod}
            onPress={() => void confirmOrder()}
          />
          <TextButton
            title="Volver a revisar entrega"
            disabled={creating}
            onPress={() => {
              setStep('delivery');
              invalidateQuote();
            }}
          />
        </>
      ) : null}

      {quoting && step !== 'delivery' ? (
        <ActivityIndicator accessibilityLabel="Cotizando pedido" color={palette.green} />
      ) : null}
      {step === 'cart' ? <ErrorBanner message={error} /> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  section: {
    backgroundColor: '#FFFFFF',
    borderColor: palette.border,
    borderRadius: 16,
    borderWidth: 1,
    marginTop: 24,
    padding: 18,
  },
  heading: {
    color: palette.dark,
    fontSize: 23,
    fontWeight: '800',
    marginBottom: 16,
  },
  subheading: {
    color: palette.dark,
    fontSize: 16,
    fontWeight: '800',
    marginBottom: 8,
    marginTop: 12,
  },
  product: {
    marginBottom: 8,
  },
  card: {
    backgroundColor: palette.background,
    borderColor: palette.border,
    borderRadius: 12,
    borderWidth: 1,
    marginVertical: 8,
    padding: 14,
  },
  cartRow: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  summaryRow: {
    borderBottomColor: palette.border,
    borderBottomWidth: 1,
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 8,
  },
  detail: {
    color: palette.dark,
    fontSize: 14,
    lineHeight: 21,
    marginBottom: 8,
  },
  total: {
    color: palette.green,
    fontSize: 17,
    fontWeight: '800',
    marginTop: 12,
  },
  options: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    marginBottom: 12,
  },
  coordinates: {
    flexDirection: 'row',
    gap: 12,
  },
  coordinate: {
    flex: 1,
  },
  notice: {
    backgroundColor: '#E8F2EB',
    borderColor: palette.border,
    borderRadius: 12,
    borderWidth: 1,
    marginVertical: 12,
    padding: 14,
  },
  noticeText: {
    color: palette.dark,
    fontSize: 14,
    lineHeight: 21,
  },
});
