// Completa app.json con lo que depende del entorno; las claves nunca se versionan.
//
// - GOOGLE_MAPS_ANDROID_API_KEY: clave de Google Maps para Android (mapas de RF-22, RF-32 y RF-35). Sin ella,
//   la app muestra las direcciones en texto en lugar del mapa, porque Google Maps se cierra sin clave.
// - GOOGLE_SERVICES_JSON: ruta al google-services.json de Firebase (push de RF-38). Sin él la app funciona,
//   pero no recibe avisos push.
module.exports = ({ config }) => {
  const mapsKey = process.env.GOOGLE_MAPS_ANDROID_API_KEY;
  const googleServicesFile = process.env.GOOGLE_SERVICES_JSON;

  return {
    ...config,
    android: {
      ...config.android,
      ...(googleServicesFile ? { googleServicesFile } : {}),
    },
    plugins: [
      ...(config.plugins ?? []),
      ['react-native-maps', mapsKey ? { androidGoogleMapsApiKey: mapsKey } : {}],
    ],
    extra: {
      ...config.extra,
      mapasAndroidHabilitados: Boolean(mapsKey),
    },
  };
};
