package co.edu.napa.notificaciones.infrastructure.push;

import co.edu.napa.notificaciones.application.ports.out.PushNotificationPort;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;

@Configuration
@ConditionalOnProperty(prefix = "napa.notifications.fcm", name = "enabled", havingValue = "true")
public class FirebaseMessagingConfiguration {
    private static final String APP_NAME = "napa-notifications";

    @Bean(destroyMethod = "delete")
    FirebaseApp napaFirebaseApp() throws IOException {
        return FirebaseApp.initializeApp(
                FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.getApplicationDefault())
                        .build(),
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
}
