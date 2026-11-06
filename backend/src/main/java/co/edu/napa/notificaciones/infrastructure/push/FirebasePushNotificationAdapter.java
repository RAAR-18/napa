package co.edu.napa.notificaciones.infrastructure.push;

import co.edu.napa.notificaciones.application.ports.out.PushDeliveryResult;
import co.edu.napa.notificaciones.application.ports.out.PushNotificationPort;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FirebasePushNotificationAdapter implements PushNotificationPort {
    private static final Logger log = LoggerFactory.getLogger(FirebasePushNotificationAdapter.class);
    private static final int MAX_BATCH_SIZE = 500;

    private final FirebaseMessaging firebaseMessaging;
    private final JdbcTemplate jdbcTemplate;

    public FirebasePushNotificationAdapter(
            FirebaseMessaging firebaseMessaging,
            JdbcTemplate jdbcTemplate
    ) {
        this.firebaseMessaging = Objects.requireNonNull(firebaseMessaging, "firebaseMessaging");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public PushDeliveryResult send(List<String> tokens, String title, String body) {
        Objects.requireNonNull(tokens, "tokens");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(body, "body");
        if (tokens.stream().anyMatch(token -> token == null || token.isBlank())) {
            throw new IllegalArgumentException("FCM tokens must not be blank");
        }
        if (tokens.isEmpty()) {
            return new PushDeliveryResult(0, 0, 0, true);
        }

        int succeeded = 0;
        int failed = 0;
        for (int start = 0; start < tokens.size(); start += MAX_BATCH_SIZE) {
            List<String> batch = tokens.subList(start, Math.min(start + MAX_BATCH_SIZE, tokens.size()));
            BatchResponse response;
            try {
                response = firebaseMessaging.sendEachForMulticast(
                        MulticastMessage.builder()
                                .addAllTokens(batch)
                                .setNotification(com.google.firebase.messaging.Notification.builder()
                                        .setTitle(title)
                                        .setBody(body)
                                        .build())
                                .build()
                );
            } catch (FirebaseMessagingException exception) {
                log.error(
                        "FCM batch delivery failed for {} device(s); in-app notifications remain available",
                        batch.size(),
                        exception
                );
                return new PushDeliveryResult(
                        tokens.size(),
                        succeeded,
                        tokens.size() - succeeded,
                        false
                );
            }

            succeeded += response.getSuccessCount();
            failed += response.getFailureCount();
            removeUnregisteredTokens(batch, response);
        }

        if (failed > 0) {
            log.warn("FCM could not deliver to {} of {} device(s)", failed, tokens.size());
        }
        return new PushDeliveryResult(tokens.size(), succeeded, failed, true);
    }

    private void removeUnregisteredTokens(List<String> tokens, BatchResponse response) {
        List<String> invalidTokens = new ArrayList<>();
        List<SendResponse> responses = response.getResponses();
        if (tokens.size() != responses.size()) {
            throw new IllegalStateException("FCM response count did not match the submitted token count");
        }
        for (int i = 0; i < responses.size(); i++) {
            SendResponse sendResponse = responses.get(i);
            if (!sendResponse.isSuccessful()
                    && sendResponse.getException() != null
                    && sendResponse.getException().getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                invalidTokens.add(tokens.get(i));
            }
        }
        if (!invalidTokens.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "DELETE FROM dispositivo_token WHERE token = ?",
                    invalidTokens,
                    invalidTokens.size(),
                    (statement, token) -> statement.setString(1, token)
            );
        }
    }
}
