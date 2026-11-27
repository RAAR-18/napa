import { fonts } from './theme';
import { CardGrid, GridCard, Icon } from './NapaUI';
import { useEffect, useMemo, useRef, useState } from 'react';
import { randomUUID } from '../lib/uuid';
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
import { ProductoCard, ProductosPagination } from './ProductosUI';
import { DateTimeField } from './DateTimeField';
import { useResumenesCalificacion } from '../hooks/useResumenesCalificacion';
import { ApiError, NetworkError } from '../lib/api';
import { getSessionGeneration, readSession } from '../lib/auth';
import { outbox, type AccionPendiente } from '../database/outbox';
import {
  borrarBorradorPedido, borrarCarritoEncolado, guardarBorradorPedido, guardarCarritoEncolado,
  guardarCotizacionOffline, leerBorradorPedido, leerCarritoEncolado, leerCotizacionOffline,
  guardarUltimaDireccion, leerUltimaDireccion,
  pedidoPendiente, prepararPedidoOffline, RUTA_CREACION_PEDIDO,
  type CarritoPedido, type DatosCotizacionGuardada,
} from '../lib/pedido-offline';
import { requestDeviceLocation } from '../lib/location';
import { obtenerMiPerfil } from '../lib/usuarios';
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
  offline?: boolean;
  direccionRecogida?: string;
  pendienteId?: string;
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
    throw new Error('Selecciona la fecha y la hora acordadas para la reserva.');
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
  neighborhood: string,
  requireNeighborhood: boolean,
): UbicacionEntregaInput {
  const lat = Number(latitude.trim().replace(',', '.'));
  const lng = Number(longitude.trim().replace(',', '.'));
  if (
    !address.trim() || !latitude.trim() || !longitude.trim() ||
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
  if (requireNeighborhood && !neighborhood.trim()) {
    throw new Error('Indica el barrio de entrega.');
  }
  if (neighborhood.trim().length > 100) {
    throw new Error('El barrio admite hasta 100 caracteres.');
  }
  return {
    latitud: lat,
    longitud: lng,
    direccion: address.trim(),
    referencia: reference.trim() || null,
    barrio: neighborhood.trim() || null,
  };
}

export function PedidoCatalogo({
  emprendimientoId,
  tipoVendedor,
  offline: catalogoOffline = false,
  direccionRecogida,
  pendienteId,
  productos,
  enabled,
  pageData,
  pageBusy,
  onPage,
}: Props) {
  // RF-02: el promedio de cada producto de la página, en una sola petición.
  const resumenes = useResumenesCalificacion(
    'PRODUCTO',
    productos.map((producto) => producto.id),
  );
  const [cart, setCart] = useState<CartItem[]>([]);
  const [quantities, setQuantities] = useState<Record<string, string>>({});
  const [step, setStep] = useState<'cart' | 'delivery' | 'summary'>('cart');
  const [modalidad, setModalidad] = useState<ModalidadPedido>(
    tipoVendedor === 'AMBULANTE' ? 'ENTREGA_DIRECTA' : 'RECOGIDA',
  );
  const [availableModalities, setAvailableModalities] = useState<ModalidadPedido[] | null>(null);
  const [address, setAddress] = useState('');
  const [latitude, setLatitude] = useState('');
  const [longitude, setLongitude] = useState('');
  const [reference, setReference] = useState('');
  const [neighborhood, setNeighborhood] = useState('');
  const [reserve, setReserve] = useState(false);
  const [reservationDate, setReservationDate] = useState('');
  const [quote, setQuote] = useState<CotizacionPedido | null>(null);
  const [paymentMethod, setPaymentMethod] = useState<MedioPagoPedido | null>(null);
  const [created, setCreated] = useState<PedidoCreado | null>(null);
  const [quoting, setQuoting] = useState(false);
  const [creating, setCreating] = useState(false);
  const [locationBusy, setLocationBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sinConexion, setSinConexion] = useState(false);
  const offline = catalogoOffline || sinConexion;
  const [cuenta, setCuenta] = useState<string | null>(null);
  const [datosGuardados, setDatosGuardados] = useState<DatosCotizacionGuardada | null>(null);
  const [direccionConfirmada, setDireccionConfirmada] = useState(false);
  const [revision, setRevision] = useState<AccionPendiente | null>(null);
  const [mensajeLocal, setMensajeLocal] = useState<string | null>(null);
  const [enCola, setEnCola] = useState(false);
  const guardando = useRef(false);
  const carritoCargado = useRef(false);
  const [borradorListo, setBorradorListo] = useState(false);
  const generacionCarrito = useRef(getSessionGeneration());
  const guardadoBorrador = useRef<Promise<void>>(Promise.resolve());
  const borradorFinalizado = useRef(false);
  const carritoEditado = useRef(false);
  const direccionEditada = useRef(false);
  const [direccionRecordada, setDireccionRecordada] = useState(false);
  const pendingRequest = useRef<{ body: string; key: string } | null>(null);

  useEffect(() => {
    if (!enabled || carritoCargado.current) return;
    let active = true;
    async function cargar() {
      const generacion = getSessionGeneration();
      const session = await readSession();
      const propietario = session?.usuarioId;
      if (!active || !propietario || session.rol !== 'CLIENTE' || generacion !== getSessionGeneration()) return;
      setCuenta(propietario);
      const accion = pendienteId
        ? (await outbox.listar()).find((item) => item.id === pendienteId && item.status === 'FALLIDA')
        : undefined;
      const pedido = accion ? pedidoPendiente(accion) : null;
      const carrito = pendienteId
        ? accion && pedido?.emprendimientoId === emprendimientoId
          ? await leerCarritoEncolado(propietario, accion.idempotencyKey)
          : null
        : await leerBorradorPedido(propietario, emprendimientoId);
      if (!active || generacion !== getSessionGeneration()) return;
      carritoCargado.current = true;
      if (pendienteId && (!carrito || !accion || !pedido)) {
        setError('Este pedido ya no está disponible para revisión.');
        return;
      }
      const direccionBorrador = carrito?.direccionBorrador;
      const tieneDireccionBorrador = direccionBorrador && Object.values(direccionBorrador).some((valor) => valor.trim());
      const ultimaDireccion = !tieneDireccionBorrador && !pedido?.ubicacionEntrega
        ? await leerUltimaDireccion(propietario).catch(() => null)
        : null;
      if (!active || generacion !== getSessionGeneration()) return;
      generacionCarrito.current = generacion;
      // Restaurar el carrito y habilitar su guardado no debe esperar a la consulta opcional del perfil.
      if (carrito && !carritoEditado.current) {
        setCart(carrito.items);
        setModalidad(carrito.modalidad);
        setReserve(carrito.reserva);
        setReservationDate(carrito.fechaReserva);
      }
      setBorradorListo(true);
      const perfil = !offline && !pendienteId && !tieneDireccionBorrador && !ultimaDireccion && !pedido?.ubicacionEntrega
        ? await Promise.resolve().then(() => obtenerMiPerfil()).catch(() => null) : null;
      if (!active || generacion !== getSessionGeneration()) return;
      const direccionGuardada = tieneDireccionBorrador ? direccionBorrador : null;
      if (direccionEditada.current) {
        // La persona ya escribió una dirección mientras cargaba: no se reemplaza.
      } else if (direccionGuardada) {
        setAddress(direccionGuardada.direccion);
        setLatitude(direccionGuardada.latitud);
        setLongitude(direccionGuardada.longitud);
        setNeighborhood(direccionGuardada.barrio);
        setReference(direccionGuardada.referencia);
      } else if (ultimaDireccion) {
        setAddress(ultimaDireccion.direccion);
        setLatitude(String(ultimaDireccion.latitud));
        setLongitude(String(ultimaDireccion.longitud));
        setNeighborhood(ultimaDireccion.barrio ?? '');
        setReference(ultimaDireccion.referencia ?? '');
        setDireccionRecordada(true);
      } else if (perfil?.id === propietario && perfil.ubicacion) {
        setAddress(perfil.ubicacion.direccion);
        setLatitude(String(perfil.ubicacion.latitud));
        setLongitude(String(perfil.ubicacion.longitud));
        setReference(perfil.ubicacion.referencia ?? '');
        setDireccionRecordada(true);
      }
      setBorradorListo(true);
      if (!carrito) return;
      if (accion) {
        setRevision(accion);
        setMensajeLocal(accion.lastError ?? 'Requiere revisión. Vuelve a cotizar el pedido.');
      }
      if (pedido?.ubicacionEntrega) {
        const ubicacion = pedido.ubicacionEntrega;
        setAddress(ubicacion.direccion);
        setLatitude(String(ubicacion.latitud));
        setLongitude(String(ubicacion.longitud));
        setNeighborhood(ubicacion.barrio ?? '');
        setReference(ubicacion.referencia ?? '');
      }
    }
    void cargar().catch(() => {
      if (!active) return;
      if (pendienteId) setError('No se pudo recuperar el carrito del pedido.');
      else setMensajeLocal('No se pudo recuperar el carrito guardado. Reabre el catálogo para intentar de nuevo.');
    });
    return () => { active = false; };
  }, [emprendimientoId, enabled, pendienteId, offline]);

  useEffect(() => {
    if (!offline || !cuenta) return;
    let active = true;
    void leerCotizacionOffline(cuenta, emprendimientoId, modalidad)
      .then((datos) => { if (active) setDatosGuardados(datos); })
      .catch(() => { if (active) setDatosGuardados(null); });
    return () => { active = false; };
  }, [cuenta, emprendimientoId, modalidad, offline]);

  const carrito: CarritoPedido = {
    items: cart, modalidad, reserva: reserve, fechaReserva: reservationDate,
    direccionBorrador: { direccion: address, latitud: latitude, longitud: longitude,
      barrio: neighborhood, referencia: reference },
  };

  useEffect(() => {
    if (!borradorListo || !cuenta || pendienteId || created || enCola || borradorFinalizado.current) return;
    const generacion = generacionCarrito.current;
    const borrador: CarritoPedido = {
      items: cart, modalidad, reserva: reserve, fechaReserva: reservationDate,
      direccionBorrador: { direccion: address, latitud: latitude, longitud: longitude,
        barrio: neighborhood, referencia: reference },
    };
    guardadoBorrador.current = guardadoBorrador.current.catch(() => undefined).then(async () => {
      if (generacion !== getSessionGeneration() || borradorFinalizado.current) return;
      // Quitar el último producto descarta el borrador en lugar de guardar un carrito vacío.
      if (borrador.items.length) await guardarBorradorPedido(cuenta, emprendimientoId, borrador);
      else await borrarBorradorPedido(cuenta, emprendimientoId);
    }).catch(() => {
      if (generacion === getSessionGeneration()) {
        setError('No se pudo guardar el carrito. Revisa el almacenamiento del dispositivo antes de salir.');
      }
    });
  }, [borradorListo, cuenta, emprendimientoId, pendienteId, created, enCola, cart, modalidad, reserve,
    reservationDate, address, latitude, longitude, neighborhood, reference]);

  async function cerrarBorrador() {
    borradorFinalizado.current = true;
    await guardadoBorrador.current.catch(() => undefined);
    if (cuenta) await borrarBorradorPedido(cuenta, emprendimientoId).catch(() => undefined);
  }
  const medioOffline = paymentMethod && datosGuardados?.medios.includes(paymentMethod)
    ? paymentMethod
    : datosGuardados?.medios.length === 1 ? datosGuardados.medios[0] : null;
  const pedidoOffline = prepararPedidoOffline(
    emprendimientoId, carrito, datosGuardados, medioOffline, direccionConfirmada,
  );

  const modalities = useMemo(() => {
    if (revision) return [modalidad];
    const options: ModalidadPedido[] =
      tipoVendedor === 'AMBULANTE' ? ['ENTREGA_DIRECTA'] : ['RECOGIDA', 'DOMICILIO'];
    return availableModalities
      ? options.filter((option) => availableModalities.includes(option))
      : options;
  }, [availableModalities, tipoVendedor, revision, modalidad]);

  const estimatedSubtotal = cart.reduce(
    (total, item) => total + Math.round(item.producto.precio * item.cantidad),
    0,
  );

  function invalidateQuote() {
    setQuote(null);
    setPaymentMethod(null);
    setDireccionConfirmada(false);
    setError(null);
  }

  function addProduct(producto: Producto) {
    setError(null);
    let cantidad: number;
    try {
      cantidad = parseCantidad(quantities[producto.id] ?? '1');
      if (cantidad <= 0 || cantidad > 99999999.99) {
        throw new Error('La cantidad debe ser positiva y tener como máximo ocho enteros y dos decimales.');
      }
    } catch (cause) {
      setError(
        cause instanceof Error ? cause.message : 'Indica una cantidad válida para el producto.',
      );
      return;
    }


    carritoEditado.current = true;
    setCart((current) => [
      ...current.filter((item) => item.producto.id !== producto.id),
      { producto, cantidad },
    ]);
    invalidateQuote();
  }

  function removeProduct(productId: string) {
    carritoEditado.current = true;
    setCart((current) => current.filter((item) => item.producto.id !== productId));
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
    if (!reserve) {
      const sinStock = cart.find(({ producto, cantidad }) => {
        const vigente = productos.find((item) => item.id === producto.id) ?? producto;
        return vigente.estado === 'AGOTADO' || cantidad > vigente.cantidadDisponible;
      });
      if (sinStock) {
        throw new Error(`La cantidad de ${sinStock.producto.nombre} supera la disponibilidad. Reduce la cantidad o elige una reserva.`);
      }
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
        neighborhood,
        modalidad === 'DOMICILIO',
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
      const input = quoteInput();
      const propietario = (await readSession())?.usuarioId;
      const result = await cotizarPedido(input);
      if (propietario && input.ubicacionEntrega && (await readSession())?.usuarioId === propietario) {
        await guardarUltimaDireccion(propietario, input.ubicacionEntrega).catch(() => undefined);
      }
      if (propietario && (await readSession())?.usuarioId === propietario && input.modalidad !== 'DOMICILIO') {
        await guardarCotizacionOffline(propietario, emprendimientoId, input.modalidad, {
          medios: result.mediosPagoDisponibles,
          direccion: input.ubicacionEntrega ?? null,
        }).catch(() => undefined);
      }
      setAvailableModalities(result.modalidadesDisponibles);
      setQuote(result);
      setPaymentMethod((current) =>
        current && result.mediosPagoDisponibles.includes(current)
          ? current
          : (result.mediosPagoDisponibles[0] ?? null),
      );
      setStep('summary');
    } catch (cause) {
      if (cause instanceof NetworkError) {
        setSinConexion(true);
        setError('No pudimos conectar con el servidor.');
        return;
      }
      if (cause instanceof ApiError && cause.code === 'BANK_ACCOUNT_REQUIRED') {
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
      ...(revision ? { preciosUnitariosEsperados: Object.fromEntries(
        quote.items.map((item) => [item.productoId, item.precioUnitario]),
      ) } : {}),
    };
  }

  async function confirmOrder() {
    setError(null);
    if (!quote || !paymentMethod || quote.mediosPagoDisponibles.length === 0) {
      setError('Este vendedor aún no tiene un medio de pago disponible.');
      return;
    }
    if (guardando.current || (pendienteId && !revision)) return;
    guardando.current = true;
    setCreating(true);
    try {
      const input = createInput();
      if (revision && cuenta) {
        const clave = randomUUID();
        await guardarCarritoEncolado(cuenta, clave, carrito);
        try {
          await outbox.corregirFallida(revision, {
            body: input, idempotencyKey: clave, descripcion: revision.descripcion,
          });
        } catch (cause) {
          await borrarCarritoEncolado(cuenta, clave).catch(() => undefined);
          throw cause;
        }
        await borrarCarritoEncolado(cuenta, revision.idempotencyKey).catch(() => undefined);
        setEnCola(true);
        void outbox.procesar().catch(() => undefined);
        return;
      }
      const body = JSON.stringify(input);
      if (!pendingRequest.current || pendingRequest.current.body !== body) {
        pendingRequest.current = { body, key: randomUUID() };
      }
      const order = await crearPedido(input, pendingRequest.current.key);
      pendingRequest.current = null;
      setCreated(order);
      await cerrarBorrador();
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 409) {
        setQuote(null);
        setPaymentMethod(null);
        setStep('delivery');
      }
      setError(await productErrorMessage(cause));
    } finally {
      guardando.current = false;
      setCreating(false);
    }
  }

  async function guardarOffline(enviar: boolean) {
    if (guardando.current || !cuenta || !cart.length || pendienteId) return;
    if (enviar && !pedidoOffline) return;
    guardando.current = true;
    setCreating(true);
    setError(null);
    try {
      if (cuenta !== await outbox.propietario()) throw new Error('La sesión cambió. Abre el catálogo nuevamente.');
      if (enviar && pedidoOffline) {
        const clave = randomUUID();
        await guardarCarritoEncolado(cuenta, clave, carrito);
        try {
          if (cuenta !== await outbox.propietario()) throw new Error('La sesión cambió. Abre el catálogo nuevamente.');
          await outbox.encolar({
            usuarioId: cuenta, method: 'POST', path: RUTA_CREACION_PEDIDO, body: pedidoOffline,
            idempotencyKey: clave, descripcion: `Pedido a ${pageData?.emprendimiento?.nombre ?? 'emprendimiento'}`,
          });
        } catch (cause) {
          await borrarCarritoEncolado(cuenta, clave).catch(() => undefined);
          throw cause;
        }
        setEnCola(true);
        await cerrarBorrador();
        void outbox.procesar().catch(() => undefined);
      } else {
        await guardarBorradorPedido(cuenta, emprendimientoId, carrito);
        setMensajeLocal('Borrador guardado en este dispositivo.');
      }
    } catch {
      setError('No se pudo guardar el pedido en este dispositivo. Intenta de nuevo.');
    } finally {
      guardando.current = false;
      setCreating(false);
    }
  }

  async function locateDevice() {
    direccionEditada.current = true;
    setDireccionRecordada(false);
    setError(null);
    setLocationBusy(true);
    try {
      const coords = await requestDeviceLocation();
      setLatitude(String(coords.latitude));
      setLongitude(String(coords.longitude));
      invalidateQuote();
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : 'No se pudo obtener tu ubicación. Puedes ingresar las coordenadas manualmente.',
      );
    } finally {
      setLocationBusy(false);
    }
  }

  if (!enabled) return null;

  if (enCola) {
    return (
      <View style={styles.section}>
        <Text style={styles.heading}>Pedido guardado para envío</Text>
        <HelperText>Consulta el resultado de la sincronización en Mis pedidos.</HelperText>
        <PrimaryButton title="Ir a Mis pedidos" onPress={() => router.push('/mis-pedidos')} />
      </View>
    );
  }

  if (created) {
    return (
      <View style={styles.section}>
        <Text accessibilityRole="header" style={styles.heading}>
          Pedido enviado
        </Text>
        <Text style={styles.detail}>Estado: {created.estado}</Text>
        <Text style={styles.total}>Total: ${created.total.toLocaleString('es-CO')} COP</Text>
        <PrimaryButton
          title="Ver estado del pedido"
          onPress={() =>
            router.push({ pathname: '/pedido-cliente', params: { pedidoId: created.id } })
          }
        />
      </View>
    );
  }

  return (
    <View style={styles.section}>
      <Text accessibilityRole="header" style={styles.heading}>
        Arma tu pedido
      </Text>
      {mensajeLocal ? <HelperText>{mensajeLocal}</HelperText> : null}
      {step === 'cart' ? (
        <>
          <HelperText>
            Puedes agregar los productos que necesites. Las cantidades y la disponibilidad se validarán
            nuevamente antes de confirmar.
          </HelperText>
          <CardGrid>
            {productos.map((producto) => (
              <GridCard key={producto.id}>
                <View style={styles.product}>
                  <ProductoCard producto={producto} resumen={resumenes[producto.id]} />
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
                  <Pressable
                    accessibilityRole="button"
                    accessibilityLabel={`Agregar ${producto.nombre} al pedido`}
                    onPress={() => addProduct(producto)}
                    style={({ pressed, hovered }: { pressed: boolean; hovered?: boolean }) => [
                      styles.addProductButton,
                      hovered && styles.addProductHovered,
                      pressed && styles.addProductPressed,
                    ]}
                  >
                    <View style={styles.addProductIcon}>
                      <Icon name="shopping-bag" size={22} color="#FFFFFF" />
                    </View>
                    <View style={styles.addProductCopy}>
                      <Text style={styles.addProductTitle}>Agregar al pedido</Text>
                      <Text style={styles.addProductSubtitle}>{producto.nombre}</Text>
                    </View>
                    <Icon name="plus" size={22} color="#FFFFFF" />
                  </Pressable>
                </View>
              </GridCard>
            ))}
          </CardGrid>
          {pageData ? (
            <ProductosPagination data={pageData} busy={pageBusy} onPage={onPage} />
          ) : null}
          {cart.length > 0 ? (
            <View style={styles.card}>
              <Text style={styles.subheading}>Tu selección</Text>
              <HelperText>
                Tu carrito se conserva en este dispositivo para esta tienda. Puedes explorar otros negocios y volver.
                Los precios y la disponibilidad se revisan antes de confirmar.
              </HelperText>
              {cart.map(({ producto, cantidad }) => (
                <View key={producto.id} style={styles.cartRow}>
                  <Text style={[styles.detail, styles.cartProductName]}>
                    {cantidad.toLocaleString('es-CO')} · {producto.nombre}
                  </Text>
                  <Pressable
                    accessibilityRole="button"
                    accessibilityLabel={`Quitar ${producto.nombre}`}
                    onPress={() => removeProduct(producto.id)}
                    style={({ pressed, hovered }: { pressed: boolean; hovered?: boolean }) => [
                      styles.removeProductButton,
                      hovered && styles.removeProductHovered,
                      pressed && styles.removeProductPressed,
                    ]}
                  >
                    <View style={styles.removeProductIcon}>
                      <Icon name="trash-2" size={18} color={palette.coralDark} />
                    </View>
                    <Text style={styles.removeProductLabel}>Quitar</Text>
                  </Pressable>
                </View>
              ))}
              <Text style={styles.total}>
                Subtotal estimado: ${estimatedSubtotal.toLocaleString('es-CO')} COP
              </Text>
            </View>
          ) : (
            <HelperText>Aún no has agregado productos.</HelperText>
          )}
          <PrimaryButton title="Continuar con la entrega" onPress={continueToDelivery} />
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
                  if (option === modalidad) return;
                  setDatosGuardados(null);
                  setModalidad(option);
                  invalidateQuote();
                }}
              />
            ))}
          </View>
          {offline ? (
            modalidad === 'ENTREGA_DIRECTA' && datosGuardados?.direccion ? (
              <>
                <Text style={styles.detail}>{datosGuardados.direccion.direccion}</Text>
                <Text style={styles.detail}>
                  {datosGuardados.direccion.latitud}, {datosGuardados.direccion.longitud}
                </Text>
                <OptionButton label="Confirmo esta dirección"
                  selected={direccionConfirmada}
                  onPress={() => setDireccionConfirmada((confirmada) => !confirmada)} />
                <HelperText>Conéctate para cambiar la dirección.</HelperText>
              </>
            ) : modalidad === 'RECOGIDA' && direccionRecogida ? (
              <Text style={styles.detail}>Punto de recogida: {direccionRecogida}</Text>
            ) : modalidad === 'ENTREGA_DIRECTA' ? (
              <HelperText>Conéctate para cotizar y guardar una dirección de entrega.</HelperText>
            ) : null
          ) : modalidad !== 'RECOGIDA' ? (
            <>
              <Text style={styles.subheading}>Ubicación de entrega</Text>
              {direccionRecordada ? <HelperText>Completamos tu dirección guardada. Revísala y cámbiala si lo necesitas.</HelperText> : null}
              <HelperText>
                La ubicación se enviará al vendedor para calcular la entrega y llevar tu pedido.
              </HelperText>
              <PrimaryButton
                title={
                  latitude && longitude
                    ? 'Actualizar mi ubicación actual'
                    : 'Usar mi ubicación actual'
                }
                busy={locationBusy}
                onPress={() => void locateDevice()}
              />
              {latitude && longitude ? (
                <View style={styles.locationBadge}>
                  <Text style={styles.locationBadgeText}>
                    ✓ Punto de entrega ({Number(latitude).toFixed(4)}, {Number(longitude).toFixed(4)})
                  </Text>
                </View>
              ) : null}
              <Field
                label="Dirección de entrega"
                maxLength={300}
                value={address}
                onChangeText={(value) => {
                  direccionEditada.current = true;
                  setAddress(value);
                  invalidateQuote();
                }}
              />
              <Field
                label={modalidad === 'DOMICILIO' ? 'Barrio' : 'Barrio (opcional)'}
                maxLength={100}
                value={neighborhood}
                onChangeText={(value) => {
                  direccionEditada.current = true;
                  setNeighborhood(value);
                  invalidateQuote();
                }}
              />
              <Field
                label="Referencia (opcional)"
                maxLength={300}
                value={reference}
                onChangeText={(value) => {
                  direccionEditada.current = true;
                  setReference(value);
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
                      direccionEditada.current = true;
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
                      direccionEditada.current = true;
                      setLongitude(value);
                      invalidateQuote();
                    }}
                  />
                </View>
              </View>
              <HelperText>
                Pulsa &quot;Usar mi ubicación actual&quot; para obtener las coordenadas de tu
                dispositivo con tu permiso, o ingresa las coordenadas manualmente si tu GPS no está disponible.
              </HelperText>
            </>
          ) : (
            <HelperText>No necesitas indicar una dirección para recoger.</HelperText>
          )}

          {!revision ? (
            <>
              <Text style={styles.subheading}>Tipo de pedido</Text>
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
                  <DateTimeField
                    label="Fecha y hora acordadas"
                    mode="datetime"
                    value={reservationDate}
                    editable={!creating && !quoting}
                    onChangeText={(value) => {
                      setReservationDate(value);
                      invalidateQuote();
                    }}
                  />
                  <HelperText>
                    Los productos agotados solo se aceptan en reservas; el vendedor confirmará si puede
                    cumplirla.
                  </HelperText>
            </>
          ) : null}
            </>
          ) : null}
          <ErrorBanner message={error} />
          {offline ? (
            <>
              {!pendienteId && !reserve && modalidad !== 'DOMICILIO' ? (
                <>
                  {cart.map(({ producto, cantidad }) => (
                    <Text key={producto.id} style={styles.detail}>
                      {cantidad} · {producto.nombre} · ${producto.precio.toLocaleString('es-CO')} COP por unidad
                    </Text>
                  ))}
                  <Text style={styles.total}>Total estimado: ${estimatedSubtotal.toLocaleString('es-CO')} COP</Text>
                  <Text style={styles.subheading}>Medio de pago</Text>
                  {datosGuardados?.medios.length ? datosGuardados.medios.map((medio) => (
                    <OptionButton key={medio} label={paymentNames[medio]}
                      selected={medioOffline === medio} onPress={() => setPaymentMethod(medio)} />
                  )) : <HelperText>Conéctate para consultar los medios de pago de esta modalidad.</HelperText>}
                  {datosGuardados?.medios.length && !medioOffline ? (
                    <HelperText>Selecciona un medio de pago para continuar.</HelperText>
                  ) : null}
                  {modalidad === 'ENTREGA_DIRECTA' && datosGuardados?.direccion && !direccionConfirmada ? (
                    <HelperText>Confirma la dirección guardada para habilitar el envío.</HelperText>
                  ) : null}
                  <PrimaryButton title="Confirmar y guardar pedido pendiente" busy={creating}
                    disabled={!pedidoOffline || !cuenta} onPress={() => void guardarOffline(true)} />
                </>
              ) : <HelperText>Conéctate para cotizar y confirmar este pedido.</HelperText>}
              {!catalogoOffline ? (
                <TextButton icon="refresh-cw" title="Reintentar conexión" disabled={creating} onPress={() => {
                  setSinConexion(false);
                  invalidateQuote();
                }} />
              ) : null}
              {!pendienteId ? (
                <TextButton icon="save" title="Guardar borrador" disabled={creating || !cuenta}
                  onPress={() => void guardarOffline(false)} />
              ) : null}
            </>
          ) : (
            <PrimaryButton title="Cotizar pedido" busy={quoting}
              disabled={Boolean(pendienteId && !revision)} onPress={() => void reviewQuote()} />
          )}
          <TextButton icon="chevron-left"
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
                {revision ? ` · ${item.precioUnitario.toLocaleString('es-CO')} COP por unidad` : ''}
              </Text>
              <Text style={styles.detail}>${item.subtotal.toLocaleString('es-CO')} COP</Text>
            </View>
          ))}
          <Text style={styles.detail}>Modalidad: {modalityNames[modalidad]}</Text>
          <Text style={styles.detail}>
            Productos: ${quote.subtotal.toLocaleString('es-CO')} COP
          </Text>
          {quote.tarifaDomicilio !== null ? (
            <Text style={styles.detail}>
              Domicilio: ${quote.tarifaDomicilio.toLocaleString('es-CO')} COP
            </Text>
          ) : null}
          <Text style={styles.total}>Total: ${quote.total.toLocaleString('es-CO')} COP</Text>
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
            <HelperText>Este vendedor aún no ha habilitado un medio de pago.</HelperText>
          )}
          {quote.pagoDomicilio ? (
            <View style={styles.notice}>
              <Text style={styles.noticeText}>
                El domicilio se paga por transferencia por el total de $
                {quote.pagoDomicilio.total.toLocaleString('es-CO')} COP cuando el vendedor acepte el
                pedido.
              </Text>
            </View>
          ) : null}
          {quote.advertencias.map((warning) => (
            <HelperText key={warning}>{warning}</HelperText>
          ))}
          <ErrorBanner message={error} />
          {offline ? (
            <HelperText>Vuelve a revisar la entrega para usar los datos guardados.</HelperText>
          ) : null}
          <PrimaryButton
            title={revision ? 'Confirmar y reenviar pedido' : 'Confirmar pedido'}
            busy={creating}
            disabled={offline || quote.mediosPagoDisponibles.length === 0 || !paymentMethod}
            onPress={() => void confirmOrder()}
          />
          <TextButton icon="chevron-left"
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
    fontFamily: fonts.sans,
    fontSize: 23,
    fontWeight: '800',
    marginBottom: 16,
  },
  subheading: {
    color: palette.dark,
    fontFamily: fonts.sans,
    fontSize: 16,
    fontWeight: '800',
    marginBottom: 8,
    marginTop: 12,
  },
  product: {
    marginBottom: 8,
  },
  addProductButton: {
    backgroundColor: palette.coral,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: '#DD5030',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    minHeight: 72,
    paddingHorizontal: 14,
    paddingVertical: 12,
    marginTop: 4,
    boxShadow: '0px 4px 10px rgba(200, 69, 41, 0.18)',
  },
  addProductHovered: { backgroundColor: '#DE5232' },
  addProductPressed: {
    backgroundColor: palette.coralDark,
    transform: [{ scale: 0.98 }],
    boxShadow: '0px 1px 3px rgba(200, 69, 41, 0.12)',
  },
  addProductIcon: {
    width: 42,
    height: 42,
    borderRadius: 12,
    backgroundColor: '#FFFFFF26',
    alignItems: 'center',
    justifyContent: 'center',
  },
  addProductCopy: { flex: 1, minWidth: 0, gap: 3 },
  addProductTitle: { fontFamily: fonts.sans, fontSize: 16, fontWeight: '800', color: '#FFFFFF' },
  addProductSubtitle: { fontFamily: fonts.sans, fontSize: 12, lineHeight: 17, color: '#FFF2E9' },
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
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    gap: 12,
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: palette.border,
  },
  cartProductName: { flexGrow: 1, flexShrink: 1, flexBasis: 140, marginBottom: 0 },
  removeProductButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    minHeight: 48,
    paddingVertical: 7,
    paddingLeft: 8,
    paddingRight: 14,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: '#F1B69D',
    backgroundColor: '#FFF0E5',
    boxShadow: '0px 2px 5px rgba(200, 69, 41, 0.08)',
  },
  removeProductHovered: { backgroundColor: palette.peach, borderColor: palette.coral },
  removeProductPressed: { backgroundColor: '#FAD1BC', transform: [{ scale: 0.96 }] },
  removeProductIcon: {
    width: 32,
    height: 32,
    borderRadius: 10,
    backgroundColor: '#E95F3D16',
    alignItems: 'center',
    justifyContent: 'center',
  },
  removeProductLabel: { fontFamily: fonts.sans, fontSize: 14, fontWeight: '800', color: palette.coralDark },
  summaryRow: {
    borderBottomColor: palette.border,
    borderBottomWidth: 1,
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 8,
  },
  detail: {
    color: palette.dark,
    fontFamily: fonts.sans,
    fontSize: 14,
    lineHeight: 21,
    marginBottom: 8,
  },
  total: {
    color: palette.green,
    fontFamily: fonts.sans,
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
    backgroundColor: '#F3E8DD',
    borderColor: palette.border,
    borderRadius: 12,
    borderWidth: 1,
    marginVertical: 12,
    padding: 14,
  },
  noticeText: {
    color: palette.dark,
    fontFamily: fonts.sans,
    fontSize: 14,
    lineHeight: 21,
  },
  locationBadge: {
    backgroundColor: '#E8F2EB',
    borderColor: '#C2DEC9',
    borderRadius: 8,
    borderWidth: 1,
    marginVertical: 8,
    padding: 10,
  },
  locationBadgeText: {
    color: palette.green,
    fontSize: 13,
    fontWeight: '700',
  },
});
