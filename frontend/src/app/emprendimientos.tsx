import { useCallback, useEffect, useMemo, useState } from 'react';
import * as Location from 'expo-location';
import { router } from 'expo-router';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';

import {
  AuthPage,
  ErrorBanner,
  OptionButton,
  PageHeading,
  TextButton,
  palette,
} from '../components/AuthUI';
import {
  ActionLink,
  CardGrid,
  DetailLine,
  EmptyState,
  FilterPanel,
  FilterRow,
  GridCard,
  Icon,
  Illustration,
  StatusBadge,
} from '../components/NapaUI';
import { categoryArt, fonts } from '../components/theme';
import { ApiError, NetworkError } from '../lib/api';
import { clearSession, getValidSession, readSession, type StoredSession } from '../lib/auth';
import { readEmprendimientosPage, saveEmprendimientosPage } from '../lib/emprendimientos-cache';
import {
  listEmprendimientos,
  type EmprendimientosFilter,
  type EmprendimientosPage,
  type TipoVendedor,
} from '../lib/emprendimientos';

const categoryNames: Record<string, string> = {
  FRUTAS_VERDURAS: 'Frutas y verduras',
  PESCADO_MARISCOS: 'Pescado y mariscos',
  ALIMENTOS_PREPARADOS: 'Alimentos preparados',
  BEBIDAS: 'Bebidas',
  SNACKS: 'Snacks',
  OTROS: 'Otros',
};

const PAGE_SIZE = 20;

export default function EmprendimientosScreen() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [page, setPage] = useState<EmprendimientosPage | null>(null);
  const [pageNumber, setPageNumber] = useState(0);
  const [tipoVendedor, setTipoVendedor] = useState<TipoVendedor | undefined>();
  const [radioMetros, setRadioMetros] = useState<number | undefined>();
  const [nearby, setNearby] = useState(false);
  const [position, setPosition] = useState<{ lat: number; lng: number } | null>(null);
  const [loading, setLoading] = useState(true);
  const [locationBusy, setLocationBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [offlineUpdatedAt, setOfflineUpdatedAt] = useState<string | null>(null);

  const filter = useMemo<EmprendimientosFilter>(
    () => ({
      page: pageNumber,
      size: PAGE_SIZE,
      tipoVendedor,
      radioMetros: nearby ? radioMetros : undefined,
      ...(nearby && position ? { lat: position.lat, lng: position.lng, cercania: true } : {}),
    }),
    [nearby, pageNumber, position, radioMetros, tipoVendedor],
  );

  useEffect(() => {
    let active = true;
    getValidSession()
      .catch(async (cause: unknown) => {
        if (!(cause instanceof NetworkError)) throw cause;
        const local = await readSession();
        if (local?.rol === 'CLIENTE') return local;
        throw cause;
      })
      .then((validSession) => {
        if (!active) {
          return;
        }
        if (validSession.rol !== 'CLIENTE') {
          setError('Solo las cuentas de cliente pueden explorar emprendimientos.');
          setLoading(false);
          return;
        }
        setSession(validSession);
      })
      .catch(async (cause: unknown) => {
        if (!active) {
          return;
        }
        if (cause instanceof ApiError && cause.status === 401) {
          await clearSession();
          router.replace('/login');
          setLoading(false);
          return;
        }
        setError(cause instanceof Error ? cause.message : 'No se pudo recuperar la sesión.');
        setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!session) {
      return;
    }
    let active = true;

    async function load() {
      try {
        const result = await listEmprendimientos(filter);
        if (!active) {
          return;
        }
        setPage(result);
        setError(null);
        setNotice(null);
        setOfflineUpdatedAt(null);
        try {
          await saveEmprendimientosPage(filter, result);
        } catch (cause) {
          if (active) {
            const detail = cause instanceof Error ? ` ${cause.message}` : '';
            setNotice(
              `El listado está actualizado, pero no se pudo guardar para usarlo sin conexión.${detail}`,
            );
          }
        }
      } catch (cause) {
        if (!active) {
          return;
        }
        if (cause instanceof ApiError) {
          if (cause.status === 401) {
            await clearSession();
            router.replace('/login');
            return;
          }
          setError(cause.message);
          return;
        }
        let cached: Awaited<ReturnType<typeof readEmprendimientosPage>>;
        try {
          cached = await readEmprendimientosPage(filter);
        } catch (cacheCause) {
          setError(
            `${cause instanceof Error ? cause.message : 'No se pudo conectar con el servicio.'} No se pudo leer el listado sin conexión: ${
              cacheCause instanceof Error ? cacheCause.message : 'error de almacenamiento.'
            }`,
          );
          return;
        }
        if (cached) {
          setPage(cached.page);
          setOfflineUpdatedAt(cached.updatedAt);
          setNotice('Sin conexión. Estás viendo el último listado guardado.');
          return;
        }
        setError(
          cause instanceof Error
            ? cause.message
            : 'No se pudo cargar el listado y no hay datos guardados.',
        );
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void load();
    return () => {
      active = false;
    };
  }, [filter, session]);

  const setSellerFilter = useCallback(
    (value?: TipoVendedor) => {
      if (value === tipoVendedor && pageNumber === 0) {
        return;
      }
      setPageNumber(0);
      setTipoVendedor(value);
      setLoading(true);
      setError(null);
      setNotice(null);
      setOfflineUpdatedAt(null);
    },
    [pageNumber, tipoVendedor],
  );

  const setRadiusFilter = useCallback(
    (value?: number) => {
      if (value === radioMetros && pageNumber === 0) {
        return;
      }
      setRadioMetros(value);
      setPageNumber(0);
      setLoading(true);
      setError(null);
      setNotice(null);
      setOfflineUpdatedAt(null);
    },
    [pageNumber, radioMetros],
  );

  async function toggleNearby() {
    if (nearby) {
      setNearby(false);
      setPosition(null);
      setPageNumber(0);
      setLoading(true);
      setError(null);
      setNotice(null);
      setOfflineUpdatedAt(null);
      return;
    }
    setLocationBusy(true);
    setError(null);
    try {
      const permission = await Location.requestForegroundPermissionsAsync();
      if (permission.status !== 'granted') {
        setNearby(false);
        setPosition(null);
        setNotice('Sin permiso de ubicación; se muestra el listado en orden reciente.');
        return;
      }
      const location = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.Balanced,
      });
      setPosition({
        lat: location.coords.latitude,
        lng: location.coords.longitude,
      });
      setLoading(true);
      setNearby(true);
      setPageNumber(0);
      setError(null);
      setNotice(null);
      setOfflineUpdatedAt(null);
    } catch (cause) {
      setError(
        cause instanceof Error
          ? `No se pudo obtener tu ubicación: ${cause.message}`
          : 'No se pudo obtener tu ubicación. Puedes continuar sin ordenar por cercanía.',
      );
    } finally {
      setLocationBusy(false);
    }
  }

  const totalPages = page ? Math.ceil(page.totalElements / page.size) : 0;

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Explorar"
        title="Explorar emprendimientos"
        description="Personas de aquí. Productos para ti."
      />
      <FilterPanel>
        <FilterRow label="Tipo de vendedor">
          <OptionButton
            label="Todos"
            selected={!tipoVendedor}
            onPress={() => setSellerFilter(undefined)}
          />
          <OptionButton
            label="Ambulantes"
            selected={tipoVendedor === 'AMBULANTE'}
            onPress={() => setSellerFilter('AMBULANTE')}
          />
          <OptionButton
            label="Punto fijo"
            selected={tipoVendedor === 'PUNTO_FIJO'}
            onPress={() => setSellerFilter('PUNTO_FIJO')}
          />
        </FilterRow>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={nearby ? 'Quitar orden por cercanía' : 'Ordenar por cercanía'}
          accessibilityState={{ disabled: locationBusy, selected: nearby }}
          disabled={locationBusy}
          onPress={() => void toggleNearby()}
          style={styles.proximity}
        >
          {locationBusy ? (
            <ActivityIndicator color={palette.coral} size="small" />
          ) : (
            <Icon name="map-pin" size={19} />
          )}
          <Text style={styles.proximityText}>Orden por cercanía</Text>
          <View style={[styles.toggle, nearby && { backgroundColor: palette.coral }]}>
            <View style={[styles.toggleThumb, nearby && { alignSelf: 'flex-end' }]} />
          </View>
        </Pressable>
        {nearby ? (
          <FilterRow label="Distancia máxima">
            <OptionButton
              label="Cualquier distancia"
              selected={radioMetros === undefined}
              onPress={() => setRadiusFilter(undefined)}
            />
            <OptionButton
              label="1 km"
              selected={radioMetros === 1000}
              onPress={() => setRadiusFilter(1000)}
            />
            <OptionButton
              label="2 km"
              selected={radioMetros === 2000}
              onPress={() => setRadiusFilter(2000)}
            />
            <OptionButton
              label="5 km"
              selected={radioMetros === 5000}
              onPress={() => setRadiusFilter(5000)}
            />
          </FilterRow>
        ) : null}
      </FilterPanel>
      {notice ? <Text style={styles.notice}>{notice}</Text> : null}
      {offlineUpdatedAt ? (
        <Text style={styles.timestamp}>
          Última actualización:{' '}
          {new Date(offlineUpdatedAt).toLocaleTimeString([], {
            hour: '2-digit',
            minute: '2-digit',
          })}
        </Text>
      ) : null}
      <ErrorBanner message={error} />
      {loading ? (
        <ActivityIndicator
          accessibilityLabel="Cargando emprendimientos"
          color={palette.green}
          style={styles.loading}
        />
      ) : null}
      {!loading && page?.content.length === 0 ? (
        <EmptyState
          title="No hay emprendimientos disponibles."
          description="Prueba con otro tipo de vendedor o amplía la distancia de búsqueda."
          icon="compass"
        />
      ) : null}
      <CardGrid>
        {!loading
          ? page?.content.map((item) => (
              <GridCard key={item.id}>
                <View style={styles.card}>
                  <View>
                    <Illustration source={categoryArt(item.categoria)} />
                    <View style={styles.openBadge}>
                      <StatusBadge
                        label={item.abierto ? 'Abierto ahora' : 'Cerrado'}
                        tone={item.abierto ? 'sage' : 'muted'}
                      />
                    </View>
                  </View>
                  <View style={styles.cardBody}>
                    <Text accessibilityRole="header" style={styles.businessName}>
                      {item.nombre}
                    </Text>
                    <Text style={styles.detail}>
                      {item.categoriaPersonalizada ?? categoryNames[item.categoria] ?? item.categoria} ·{' '}
                      {item.tipoVendedor === 'AMBULANTE' ? 'Ambulante' : 'Punto fijo'}
                    </Text>
                    <DetailLine icon="map-pin">
                      {item.ubicacion.direccion ?? 'Ubicación aproximada'}
                    </DetailLine>
                    <DetailLine icon="clock">
                      {item.horaApertura}–{item.horaCierre}
                    </DetailLine>
                    {item.distanciaMetros !== null ? (
                      <DetailLine icon="navigation">
                        A {Math.round(item.distanciaMetros)} m
                      </DetailLine>
                    ) : null}
                    <ActionLink
                      label="Ver productos"
                      accessibilityLabel={`Ver productos de ${item.nombre}`}
                      onPress={() =>
                        router.push({
                          pathname: '/catalogo',
                          params: {
                            emprendimientoId: item.id, tipoVendedor: item.tipoVendedor,
                            ...(item.tipoVendedor === 'PUNTO_FIJO' && item.ubicacion?.direccion ? { direccionRecogida: item.ubicacion.direccion } : {}),
                          },
                        })
                      }
                    />
                    <Pressable
                      accessibilityRole="button"
                      accessibilityLabel={`Ver detalle de ${item.nombre}`}
                      style={styles.detailButton}
                      onPress={() =>
                        router.push({
                          pathname: '/emprendimiento-detalle',
                          params: { emprendimientoId: item.id },
                        })
                      }
                    >
                      <Text style={styles.detailButtonText}>Ver detalle</Text>
                    </Pressable>
                  </View>
                </View>
              </GridCard>
            ))
          : null}
      </CardGrid>
      {page && page.totalElements > 0 ? (
        <View style={styles.pagination}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Página anterior"
            disabled={pageNumber === 0 || loading}
            onPress={() => {
              setLoading(true);
              setPageNumber((current) => Math.max(0, current - 1));
            }}
            style={styles.pageButton}
          >
            <Text style={styles.pageButtonText}>Anterior</Text>
          </Pressable>
          <Text style={styles.pageCount}>
            {pageNumber + 1} de {totalPages}
          </Text>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Página siguiente"
            disabled={pageNumber + 1 >= totalPages || loading}
            onPress={() => {
              setLoading(true);
              setPageNumber((current) => current + 1);
            }}
            style={styles.pageButton}
          >
            <Text style={styles.pageButtonText}>Siguiente</Text>
          </Pressable>
        </View>
      ) : null}
      <TextButton title="Volver a mi cuenta" onPress={() => router.replace('/session')} />
    </AuthPage>
  );
}

const styles = StyleSheet.create({
  sectionLabel: {
    color: palette.dark,
    fontSize: 14,
    fontWeight: '700',
    marginBottom: 8,
  },
  filters: {
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
  notice: {
    color: palette.muted,
    fontSize: 13,
    lineHeight: 19,
    marginTop: 12,
  },
  timestamp: {
    color: palette.muted,
    fontSize: 12,
    marginTop: 4,
  },
  loading: {
    marginVertical: 28,
  },
  empty: {
    backgroundColor: '#FFFFFF',
    borderColor: palette.border,
    borderRadius: 12,
    borderWidth: 1,
    color: palette.muted,
    marginTop: 16,
    padding: 18,
    textAlign: 'center',
  },
  card: {
    backgroundColor: palette.white,
    borderColor: palette.border,
    borderRadius: 14,
    borderWidth: 1,
    flex: 1,
    overflow: 'hidden',
  },
  cardBody: { padding: 18, flex: 1 },
  openBadge: { position: 'absolute', top: 12, right: 12 },
  detailButton: { minHeight: 35, alignItems: 'center', justifyContent: 'center', marginTop: 5 },
  detailButtonText: {
    fontFamily: fonts.sans,
    color: palette.dark,
    fontSize: 13,
    fontWeight: '700',
    textDecorationLine: 'underline',
  },
  proximity: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingVertical: 8,
    alignSelf: 'flex-start',
  },
  proximityText: { fontFamily: fonts.sans, color: palette.dark, fontSize: 13, fontWeight: '600' },
  toggle: { width: 44, height: 25, borderRadius: 15, padding: 3, backgroundColor: '#D6C7BD' },
  toggleThumb: { width: 19, height: 19, borderRadius: 10, backgroundColor: '#FFFFFF' },
  businessName: {
    color: palette.dark,
    fontFamily: fonts.display,
    fontSize: 25,
    lineHeight: 30,
    fontWeight: '700',
  },
  detail: {
    color: palette.muted,
    fontSize: 14,
    lineHeight: 21,
    marginTop: 5,
  },
  distance: {
    color: palette.green,
    fontSize: 13,
    fontWeight: '700',
    marginTop: 8,
  },
  pagination: {
    alignItems: 'center',
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    justifyContent: 'space-between',
    marginTop: 18,
  },
  pageButton: {
    paddingHorizontal: 10,
    paddingVertical: 12,
  },
  pageButtonText: {
    color: palette.green,
    fontWeight: '700',
  },
  pageCount: {
    color: palette.muted,
    fontSize: 13,
  },
});
