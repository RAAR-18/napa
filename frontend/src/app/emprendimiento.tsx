import { useEffect, useState } from 'react';
import { router } from 'expo-router';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';

import {
  AuthPage,
  ErrorBanner,
  Field,
  FormFooter,
  HelperText,
  OptionButton,
  PageHeading,
  PrimaryButton,
  TextButton,
  palette,
} from '../components/AuthUI';
import { ApiError } from '../lib/api';
import {
  createEmprendimiento,
  type CategoriaEmprendimiento,
  type CrearEmprendimientoInput,
  type Emprendimiento,
} from '../lib/emprendimientos';
import { clearSession, getValidSession, type StoredSession } from '../lib/auth';

const categories: { value: CategoriaEmprendimiento; label: string }[] = [
  { value: 'FRUTAS_VERDURAS', label: 'Frutas y verduras' },
  { value: 'PESCADO_MARISCOS', label: 'Pescado y mariscos' },
  { value: 'ALIMENTOS_PREPARADOS', label: 'Alimentos preparados' },
  { value: 'BEBIDAS', label: 'Bebidas' },
  { value: 'SNACKS', label: 'Snacks' },
  { value: 'OTROS', label: 'Otros' },
];

export default function EmprendimientoScreen() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [loading, setLoading] = useState(true);
  const [nombre, setNombre] = useState('');
  const [categoria, setCategoria] =
    useState<CategoriaEmprendimiento>('OTROS');
  const [descripcion, setDescripcion] = useState('');
  const [horaApertura, setHoraApertura] = useState('');
  const [horaCierre, setHoraCierre] = useState('');
  const [latitud, setLatitud] = useState('');
  const [longitud, setLongitud] = useState('');
  const [direccion, setDireccion] = useState('');
  const [referencia, setReferencia] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<Emprendimiento | null>(null);

  const isFixedVendor = session?.rol === 'VENDEDOR_PUNTO_FIJO';

  useEffect(() => {
    let active = true;
    getValidSession()
      .then((validSession) => {
        if (!active) {
          return;
        }
        if (
          validSession.rol !== 'VENDEDOR_AMBULANTE' &&
          validSession.rol !== 'VENDEDOR_PUNTO_FIJO'
        ) {
          setError('Solo las cuentas de vendedor pueden crear un emprendimiento.');
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
          return;
        }
        setError(
          cause instanceof Error
            ? cause.message
            : 'No se pudo recuperar la sesión.',
        );
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, []);

  function formInput(): CrearEmprendimientoInput | null {
    const latitude = Number(latitud.trim().replace(',', '.'));
    const longitude = Number(longitud.trim().replace(',', '.'));
    const validTime = (value: string) =>
      /^(?:[01]\d|2[0-3]):[0-5]\d$/.test(value.trim());
    if (
      !nombre.trim() ||
      nombre.trim().length > 100 ||
      !descripcion.trim() ||
      descripcion.trim().length > 500 ||
      !validTime(horaApertura) ||
      !validTime(horaCierre) ||
      !latitud.trim() ||
      !longitud.trim() ||
      !Number.isFinite(latitude) ||
      latitude < -90 ||
      latitude > 90 ||
      !Number.isFinite(longitude) ||
      longitude < -180 ||
      longitude > 180 ||
      (isFixedVendor && !direccion.trim())
    ) {
      return null;
    }

    return {
      nombre: nombre.trim(),
      categoria,
      descripcion: descripcion.trim(),
      horaApertura: horaApertura.trim(),
      horaCierre: horaCierre.trim(),
      ubicacion: {
        latitud: latitude,
        longitud: longitude,
        direccion: direccion.trim() || null,
        referencia: referencia.trim() || null,
      },
    };
  }

  async function submit() {
    setError(null);
    if (!session) {
      setError('Inicia sesión con una cuenta de vendedor para continuar.');
      return;
    }
    const input = formInput();
    if (!input) {
      setError(
        'Revisa los campos obligatorios, el horario en formato HH:mm y las coordenadas válidas.',
      );
      return;
    }

    setBusy(true);
    try {
      setCreated(await createEmprendimiento(input));
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) {
        await clearSession();
        router.replace('/login');
        return;
      }
      setError(
        cause instanceof Error
          ? cause.message
          : 'No se pudo crear el emprendimiento. Inténtalo de nuevo.',
      );
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return (
      <AuthPage>
        <PageHeading
          eyebrow="Emprendimiento"
          title="Preparando el formulario"
          description="Estamos validando tu sesión."
        />
        <ActivityIndicator color={palette.green} />
      </AuthPage>
    );
  }

  if (created) {
    return (
      <AuthPage>
        <PageHeading
          eyebrow="Emprendimiento publicado"
          title={created.nombre}
          description="Tu emprendimiento quedó activo y disponible para los clientes."
        />
        <View style={styles.successCard}>
          <Text style={styles.successTitle}>Creación exitosa</Text>
          <Text style={styles.successText}>
            Categoría: {categories.find((item) => item.value === created.categoria)?.label}
          </Text>
          <Text style={styles.successText}>Estado: {created.estado}</Text>
        </View>
        <PrimaryButton
          title="Agregar mis productos"
          onPress={() => router.replace('/mis-productos')}
        />
        <TextButton
          title="Volver a mi cuenta"
          onPress={() => router.replace('/session')}
        />
      </AuthPage>
    );
  }

  return (
    <AuthPage>
      <PageHeading
        eyebrow="RF-28 · Tu negocio"
        title="Crea tu emprendimiento"
        description="Completa los datos para publicar tu negocio en Ñapa."
      />
      <ErrorBanner message={error} />
      <Field
        label="Nombre del emprendimiento"
        maxLength={100}
        onChangeText={setNombre}
        placeholder="Ej. Frutas de Ana"
        value={nombre}
      />
      <Text style={styles.label}>Categoría</Text>
      <View style={styles.options}>
        {categories.map((option) => (
          <OptionButton
            key={option.value}
            label={option.label}
            onPress={() => setCategoria(option.value)}
            selected={categoria === option.value}
          />
        ))}
      </View>
      <Field
        label="Descripción"
        maxLength={500}
        multiline
        onChangeText={setDescripcion}
        placeholder="Cuéntales a tus clientes qué ofreces"
        value={descripcion}
      />
      <View style={styles.row}>
        <View style={styles.column}>
          <Field
            label="Hora de apertura"
            onChangeText={setHoraApertura}
            placeholder="08:00"
            value={horaApertura}
          />
        </View>
        <View style={styles.column}>
          <Field
            label="Hora de cierre"
            onChangeText={setHoraCierre}
            placeholder="18:00"
            value={horaCierre}
          />
        </View>
      </View>
      <HelperText>Usa el formato de 24 horas HH:mm.</HelperText>
      <Text style={styles.label}>Ubicación</Text>
      <Field
        keyboardType="decimal-pad"
        label="Latitud"
        onChangeText={setLatitud}
        placeholder="Ej. 4.7110"
        value={latitud}
      />
      <Field
        keyboardType="decimal-pad"
        label="Longitud"
        onChangeText={setLongitud}
        placeholder="Ej. -74.0721"
        value={longitud}
      />
      <HelperText>
        Ingresa las coordenadas del lugar. La latitud va de -90 a 90 y la
        longitud de -180 a 180.
      </HelperText>
      <Field
        label={isFixedVendor ? 'Dirección del punto (obligatoria)' : 'Dirección (opcional)'}
        maxLength={300}
        onChangeText={setDireccion}
        placeholder="Calle, carrera, número o sector"
        value={direccion}
      />
      <Field
        label="Referencia de ubicación"
        maxLength={300}
        onChangeText={setReferencia}
        placeholder="Punto de referencia para encontrar el negocio"
        value={referencia}
      />
      {isFixedVendor ? null : (
        <HelperText>
          Como vendedor ambulante puedes usar una ubicación aproximada y no
          necesitas registrar una dirección.
        </HelperText>
      )}
      <PrimaryButton
        busy={busy}
        disabled={!session}
        title="Publicar emprendimiento"
        onPress={submit}
      />
      <FormFooter>
        <TextButton
          title="Volver a mi cuenta"
          onPress={() => router.replace('/session')}
        />
      </FormFooter>
    </AuthPage>
  );
}

const styles = StyleSheet.create({
  label: {
    color: palette.dark,
    fontSize: 14,
    fontWeight: '700',
    marginBottom: 8,
  },
  options: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    marginBottom: 12,
  },
  row: {
    flexDirection: 'row',
    gap: 12,
  },
  column: {
    flex: 1,
  },
  successCard: {
    backgroundColor: '#E8F2EB',
    borderColor: palette.border,
    borderRadius: 16,
    borderWidth: 1,
    padding: 18,
  },
  successTitle: {
    color: palette.green,
    fontSize: 18,
    fontWeight: '800',
    marginBottom: 8,
  },
  successText: {
    color: palette.dark,
    fontSize: 14,
    lineHeight: 21,
  },
});
