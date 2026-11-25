import { useLocalSearchParams, router } from "expo-router";
import { useEffect, useRef, useState } from "react";

import {
  AuthPage,
  ErrorBanner,
  Field,
  FormFooter,
  HelperText,
  PageHeading,
  PrimaryButton,
  SuccessNotice,
  TextButton,
} from "../components/AuthUI";
import {
  CanalVerificacionSelector,
  describirCanal,
} from "../components/CanalVerificacionSelector";
import { CelularField } from "../components/CelularField";
import { ApiError } from "../lib/api";
import {
  confirmRegistration,
  requestVerification,
  resetPassword,
  type VerificationChannel,
  type VerificationPurpose,
} from "../lib/auth";
import { validarCorreo } from "../lib/correo";

function formatoEspera(segundos: number): string {
  const minutos = Math.floor(segundos / 60);
  const resto = segundos % 60;
  return `${minutos}:${String(resto).padStart(2, "0")}`;
}

export default function VerifyScreen() {
  const params = useLocalSearchParams<{
    celular?: string;
    proposito?: string;
    canal?: string;
    espera?: string;
    tieneCorreo?: string;
  }>();
  // Sin celular en la ruta (botón "¿Tienes problemas para iniciar sesión?") se le pide aquí.
  const celularRuta = typeof params.celular === "string" ? params.celular : "";
  const [celularEscrito, setCelularEscrito] = useState("");
  const [correoEscrito, setCorreoEscrito] = useState("");
  const [canal, setCanal] = useState<VerificationChannel>(
    params.canal === "CORREO" || (params.canal !== "SMS" && params.tieneCorreo === "1")
      ? "CORREO" : "SMS",
  );
  // Sin celular en la ruta y con "correo" elegido, la cuenta se identifica por su correo.
  const porCorreo = !celularRuta && canal === "CORREO";
  // Tras el primer envío se usa siempre la misma cuenta, aunque luego cambie el medio elegido.
  const [cuentaEnviada, setCuentaEnviada] = useState<string | null>(null);
  const celular =
    cuentaEnviada ??
    (celularRuta ||
      (porCorreo ? correoEscrito.trim().toLowerCase() : celularEscrito));
  const proposito: VerificationPurpose =
    params.proposito === "RECUPERACION_CONTRASENA"
      ? "RECUPERACION_CONTRASENA"
      : "REGISTRO";
  const isRecovery = proposito === "RECUPERACION_CONTRASENA";
  // Si no se sabe, se ofrece el correo; el backend avisa si la cuenta no tiene uno.
  const tieneCorreo = params.tieneCorreo !== "0";
  const errorCorreo = porCorreo ? validarCorreo(correoEscrito) : null;
  const esperaInicial = Number(params.espera);
  // En la recuperación el código ya salió; en el registro primero se pregunta el medio.
  const [canalEnviado, setCanalEnviado] = useState<VerificationChannel | null>(
    isRecovery ? (params.canal === "CORREO" ? "CORREO" : "SMS") : null,
  );
  // Cada medio tiene su propia espera: si el SMS no llega se puede pedir enseguida por correo.
  const [esperas, setEsperas] = useState<Record<VerificationChannel, number>>(
    () => {
      const inicial =
        Number.isFinite(esperaInicial) && esperaInicial > 0
          ? Math.round(esperaInicial)
          : 0;
      return {
        SMS: params.canal === "CORREO" ? 0 : inicial,
        CORREO: params.canal === "CORREO" ? inicial : 0,
      };
    },
  );
  const medioActual: VerificationChannel = celular.includes("@")
    ? "CORREO"
    : canal;
  const espera = esperas[medioActual];
  const hayEspera = esperas.SMS > 0 || esperas.CORREO > 0;
  function setEspera(medio: VerificationChannel, segundos: number) {
    setEsperas((actual) => ({ ...actual, [medio]: segundos }));
  }
  const [codigo, setCodigo] = useState("");
  const [contrasenaNueva, setContrasenaNueva] = useState("");
  const operando = useRef(false);
  const [busy, setBusy] = useState(false);
  const [sending, setSending] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!hayEspera) {
      return undefined;
    }
    const timer = setTimeout(
      () =>
        setEsperas((actual) => ({
          SMS: Math.max(0, actual.SMS - 1),
          CORREO: Math.max(0, actual.CORREO - 1),
        })),
      1000,
    );
    return () => clearTimeout(timer);
  }, [esperas, hayEspera]);

  async function submit() {
    if (operando.current) return;
    operando.current = true;
    setError(null);
    setMessage(null);
    setBusy(true);
    try {
      if (isRecovery) {
        await resetPassword(celular, codigo, contrasenaNueva);
        router.replace({
          pathname: "/login",
          params: { passwordReset: "true" },
        });
      } else {
        const session = await confirmRegistration(celular, codigo);
        const esVendedor = session.rol === "VENDEDOR_PUNTO_FIJO" || session.rol === "VENDEDOR_AMBULANTE";
        router.replace(esVendedor ? "/emprendimiento" : "/session");
      }
    } catch (cause) {
      const attempts =
        cause instanceof ApiError && cause.code === "VALIDATION_ERROR"
          ? " Revisa el código de seis dígitos o solicita otro código."
          : "";
      setError(
        cause instanceof Error
          ? `${cause.message}${attempts}`
          : "No se pudo verificar el código. Inténtalo de nuevo.",
      );
    } finally {
      operando.current = false;
      setBusy(false);
    }
  }

  async function send() {
    if (operando.current) return;
    if (canal === "CORREO" && !tieneCorreo) {
      setError("Tu cuenta no tiene correo. Elige recibir el código por SMS.");
      return;
    }
    setError(null);
    setMessage(null);
    operando.current = true;
    setSending(true);
    try {
      const segundos = await requestVerification(celular, proposito, canal);
      setCanalEnviado(celular.includes("@") ? "CORREO" : canal);
      if (celular.includes("@")) {
        setCanal("CORREO");
      }
      setCuentaEnviada(celular);
      setEspera(medioActual, segundos);
      setMessage(`Te enviamos un código por ${describirCanal(canal)}.`);
    } catch (cause) {
      const retry =
        cause instanceof ApiError && cause.code === "RATE_LIMITED"
          ? Number(cause.details.retryAfter)
          : 0;
      if (retry > 0) {
        setEspera(medioActual, Math.round(retry));
      }
      setError(
        cause instanceof Error ? cause.message : "No se pudo enviar el código.",
      );
    } finally {
      operando.current = false;
      setSending(false);
    }
  }

  const selector = (
    <>
      <CanalVerificacionSelector
        label={
          canalEnviado
            ? "¿No te llegó? Pide otro código"
            : "¿Prefieres verificar tu cuenta por SMS o por correo?"
        }
        disabled={busy || sending}
        onChange={setCanal}
        value={canal}
      />
      {canal === "CORREO" && !tieneCorreo ? (
        <HelperText>
          No registraste un correo, así que el código solo puede llegar por SMS.
        </HelperText>
      ) : null}
    </>
  );

  if (!canalEnviado) {
    return (
      <AuthPage>
        <PageHeading
          eyebrow="Verifica tu cuenta"
          title="¿Cómo quieres recibir tu código?"
          description={
            celularRuta
              ? tieneCorreo
                ? `Tu cuenta quedó creada. Recomendamos verificar por correo; también puedes elegir SMS al ${celular}.`
                : `Tu cuenta quedó creada. Te enviaremos el código por SMS al ${celular}.`
              : "Elige cómo recibir el código y escribe el correo o el celular con el que creaste tu cuenta."
          }
        />
        <ErrorBanner message={error} />
        {selector}
        {celularRuta ? null : porCorreo ? (
          <>
            <Field
              autoCapitalize="none"
              keyboardType="email-address"
              label="Correo de tu cuenta"
              maxLength={254}
              onChangeText={setCorreoEscrito}
              placeholder="nombre@gmail.com"
              value={correoEscrito}
            />
            {correoEscrito.trim() && errorCorreo ? (
              <HelperText>{errorCorreo}</HelperText>
            ) : null}
          </>
        ) : (
          <CelularField
            label="Celular de tu cuenta"
            onChange={setCelularEscrito}
          />
        )}
        <PrimaryButton
          busy={sending}
          disabled={
            espera > 0 ||
            (porCorreo
              ? !correoEscrito.trim() || Boolean(errorCorreo)
              : celular.length < 10) ||
            (canal === "CORREO" && !tieneCorreo)
          }
          title={
            espera > 0
              ? `Espera ${formatoEspera(espera)}`
              : `Enviar código por ${describirCanal(canal)}`
          }
          onPress={send}
        />
        <FormFooter>
          <TextButton
            title="Volver al inicio de sesión"
            onPress={() => router.replace("/login")}
          />
        </FormFooter>
      </AuthPage>
    );
  }

  const destino =
    canalEnviado === "CORREO" ? "a tu correo" : `por SMS al ${celular}`;

  return (
    <AuthPage>
      <PageHeading
        eyebrow={isRecovery ? "Recuperación segura" : "Verifica tu cuenta"}
        title={isRecovery ? "Crea una contraseña nueva" : "Ingresa tu código"}
        description={
          isRecovery
            ? `Escribe el código que enviamos ${destino} y elige una nueva contraseña.`
            : `Tu cuenta quedará activa cuando escribas el código que enviamos ${destino}.`
        }
      />
      <ErrorBanner message={error} />
      <SuccessNotice message={message} />
      <Field
        autoCapitalize="none"
        keyboardType="number-pad"
        label="Código de verificación"
        maxLength={6}
        onChangeText={(value) => setCodigo(value.replace(/[^0-9]/g, ""))}
        placeholder="Código de 6 dígitos"
        value={codigo}
      />
      {isRecovery ? (
        <>
          <Field
            autoCapitalize="none"
            label="Nueva contraseña"
            maxLength={128}
            onChangeText={setContrasenaNueva}
            placeholder="Mínimo 8 caracteres"
            secureTextEntry
            showPasswordToggle
            value={contrasenaNueva}
          />
          <HelperText>Debe tener entre 8 y 128 caracteres.</HelperText>
        </>
      ) : null}
      <PrimaryButton
        busy={busy || sending}
        disabled={
          codigo.length !== 6 || (isRecovery && contrasenaNueva.length < 8)
        }
        title={isRecovery ? "Actualizar contraseña" : "Verificar y continuar"}
        onPress={submit}
      />
      {celular.includes("@") ? null : selector}
      {espera > 0 ? (
        <HelperText>
          {`Podrás pedir otro código por ${describirCanal(medioActual)} en ${formatoEspera(espera)}. Si no te llega, pídelo por el otro medio.`}
        </HelperText>
      ) : null}
      <FormFooter>
        <TextButton
          disabled={
            sending ||
            busy ||
            espera > 0 ||
            (canal === "CORREO" && !tieneCorreo)
          }
          title={
            sending
              ? "Enviando código…"
              : `Enviar otro código por ${describirCanal(canal)}`
          }
          onPress={send}
        />
        <TextButton
          title="Volver al inicio de sesión"
          onPress={() => router.replace("/login")}
        />
      </FormFooter>
    </AuthPage>
  );
}
