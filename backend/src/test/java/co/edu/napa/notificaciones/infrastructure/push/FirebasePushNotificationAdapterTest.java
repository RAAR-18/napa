package co.edu.napa.notificaciones.infrastructure.push;

import co.edu.napa.notificaciones.application.ports.out.PushDeliveryResult;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FirebasePushNotificationAdapterTest {
    @Test
    void sendsInBatchesOfAtMostFiveHundredTokens() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        BatchResponse firstBatch = batchResponse(500, 0, successfulResponses(500));
        BatchResponse secondBatch = batchResponse(1, 0, successfulResponses(1));
        when(messaging.sendEachForMulticast(any(MulticastMessage.class)))
                .thenReturn(firstBatch, secondBatch);
        FirebasePushNotificationAdapter adapter = new FirebasePushNotificationAdapter(messaging, jdbcTemplate);
        List<String> tokens = tokens(501);

        PushDeliveryResult result = adapter.send(tokens, "Title", "Body");

        assertEquals(new PushDeliveryResult(501, 501, 0, true), result);
        verify(messaging, org.mockito.Mockito.times(2)).sendEachForMulticast(any(MulticastMessage.class));
    }

    @Test
    void removesOnlyTokensFirebaseReportsAsUnregistered() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SendResponse unregistered = failedResponse(MessagingErrorCode.UNREGISTERED);
        SendResponse invalidArgument = failedResponse(MessagingErrorCode.INVALID_ARGUMENT);
        BatchResponse response = batchResponse(0, 2, List.of(unregistered, invalidArgument));
        when(messaging.sendEachForMulticast(any(MulticastMessage.class)))
                .thenReturn(response);
        FirebasePushNotificationAdapter adapter = new FirebasePushNotificationAdapter(messaging, jdbcTemplate);
        List<String> tokens = tokens(2);

        PushDeliveryResult result = adapter.send(tokens, "Title", "Body");

        assertEquals(new PushDeliveryResult(2, 0, 2, true), result);
        verify(jdbcTemplate).batchUpdate(
                eq("DELETE FROM dispositivo_token WHERE token = ?"),
                eq(List.of(tokens.get(0))),
                eq(1),
                org.mockito.ArgumentMatchers.<ParameterizedPreparedStatementSetter<String>>any()
        );
    }

    @Test
    void reportsProviderFailureWithoutHidingInAppNotifications() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(messaging.sendEachForMulticast(any(MulticastMessage.class)))
                .thenThrow(mock(FirebaseMessagingException.class));
        FirebasePushNotificationAdapter adapter = new FirebasePushNotificationAdapter(messaging, jdbcTemplate);

        PushDeliveryResult result = adapter.send(tokens(2), "Title", "Body");

        assertEquals(new PushDeliveryResult(2, 0, 2, false), result);
        verify(jdbcTemplate, never()).batchUpdate(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<List<String>>any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.<ParameterizedPreparedStatementSetter<String>>any()
        );
    }

    @Test
    void rejectsBlankTokensInsteadOfSendingMalformedRequests() {
        FirebasePushNotificationAdapter adapter = new FirebasePushNotificationAdapter(
                mock(FirebaseMessaging.class),
                mock(JdbcTemplate.class)
        );

        assertThrows(IllegalArgumentException.class, () -> adapter.send(List.of(""), "Title", "Body"));
    }

    private BatchResponse batchResponse(int successCount, int failureCount, List<SendResponse> responses) {
        BatchResponse response = mock(BatchResponse.class);
        when(response.getSuccessCount()).thenReturn(successCount);
        when(response.getFailureCount()).thenReturn(failureCount);
        when(response.getResponses()).thenReturn(responses);
        return response;
    }

    private SendResponse failedResponse(MessagingErrorCode errorCode) {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(errorCode);
        SendResponse response = mock(SendResponse.class);
        when(response.isSuccessful()).thenReturn(false);
        when(response.getException()).thenReturn(exception);
        return response;
    }

    private List<SendResponse> successfulResponses(int count) {
        List<SendResponse> responses = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            SendResponse response = mock(SendResponse.class);
            when(response.isSuccessful()).thenReturn(true);
            responses.add(response);
        }
        return responses;
    }

    private List<String> tokens(int count) {
        List<String> tokens = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            tokens.add("push-token-" + i);
        }
        return tokens;
    }
}
