package co.edu.napa.notificaciones.infrastructure.push;

import co.edu.napa.notificaciones.application.ports.out.PushNotificationPort;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Activa el envío push por FCM con {@code NAPA_NOTIFICATIONS_FCM_ENABLED=true}.
 *
 * <p>Credenciales de la cuenta de servicio de Firebase, en este orden:
 * <ol>
 *   <li>{@code NAPA_NOTIFICATIONS_FCM_CREDENTIALS_JSON}: el contenido del JSON, tal cual o en base64.
 *       Útil en plataformas donde subir un archivo es incómodo.</li>
 *   <li>{@code GOOGLE_APPLICATION_CREDENTIALS}: ruta al archivo JSON (Application Default Credentials).</li>
 * </ol>
 */
@Configuration
@ConditionalOnProperty(prefix = "napa.notifications.fcm", name = "enabled", havingValue = "true")
public class FirebaseMessagingConfiguration {
    private static final Logger log = LoggerFactory.getLogger(FirebaseMessagingConfiguration.class);
    private static final String APP_NAME = "napa-notifications";

    @Bean(destroyMethod = "delete")
    FirebaseApp napaFirebaseApp(
            @Value("${napa.notifications.fcm.credentials-json:}") String credentialsJson
    ) throws IOException {
        GoogleCredentials credentials;
        if (credentialsJson != null && !credentialsJson.isBlank()) {
            try (InputStream json = new ByteArrayInputStream(jsonBytes(credentialsJson))) {
                credentials = GoogleCredentials.fromStream(json);
            }
            log.info("FCM push enabled with credentials from NAPA_NOTIFICATIONS_FCM_CREDENTIALS_JSON");
        } else {
            credentials = GoogleCredentials.getApplicationDefault();
            log.info("FCM push enabled with Application Default Credentials (GOOGLE_APPLICATION_CREDENTIALS)");
        }
        return FirebaseApp.initializeApp(
                FirebaseOptions.builder().setCredentials(credentials).build(),
                APP_NAME
        );
    }

    @Bean
    FirebaseMessaging napaFirebaseMessaging(FirebaseApp napaFirebaseApp) {
        return FirebaseMessaging.getInstance(napaFirebaseApp);
    }

    @Bean
    PushNotificationPort pushNotificationPort(
            FirebaseMessaging napaFirebaseMessaging,
            JdbcTemplate jdbcTemplate
    ) {
        return new FirebasePushNotificationAdapter(napaFirebaseMessaging, jdbcTemplate);
    }

    /** Acepta el JSON en texto plano o codificado en base64. */
    static byte[] jsonBytes(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("{")) {
            return trimmed.getBytes(StandardCharsets.UTF_8);
        }
        try {
            return Base64.getDecoder().decode(trimmed.replaceAll("\\s", ""));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "NAPA_NOTIFICATIONS_FCM_CREDENTIALS_JSON must be the service account JSON or its base64 encoding",
                    exception
            );
        }
    }
}
