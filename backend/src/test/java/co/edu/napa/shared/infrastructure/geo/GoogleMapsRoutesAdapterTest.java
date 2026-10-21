package co.edu.napa.shared.infrastructure.geo;

import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.domain.geo.GeoPoint;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleMapsRoutesAdapterTest {
    @Test
    void computesRouteAndCachesResponse() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(null);

        RestClient.Builder builder = RestClient.builder().baseUrl("https://routes.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://routes.googleapis.com/directions/v2:computeRoutes"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", "test-key"))
                .andExpect(header(
                        "X-Goog-FieldMask",
                        "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline"
                ))
                .andRespond(withSuccess(
                        """
                        {
                          "routes": [{
                            "distanceMeters": 1240,
                            "duration": "84s",
                            "polyline": {"encodedPolyline": "encoded-route"}
                          }]
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        GoogleMapsRoutesAdapter adapter = new GoogleMapsRoutesAdapter(
                builder.build(),
                "test-key",
                redisTemplate,
                JsonMapper.builder().build()
        );

        var result = adapter.computeRoute(new GeoPoint(11.24, -74.20), new GeoPoint(11.25, -74.21));

        assertEquals(1240, result.distanceMeters());
        assertEquals(Duration.ofSeconds(84), result.duration());
        assertEquals("encoded-route", result.encodedPolyline());
        verify(values).set(anyString(), anyString(), eq(Duration.ofHours(24)));
        server.verify();
    }

    @Test
    void reportsMissingApiKeyAsExternalServiceUnavailable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(null);

        GoogleMapsRoutesAdapter adapter = new GoogleMapsRoutesAdapter(
                RestClient.builder().baseUrl("https://routes.googleapis.com").build(),
                "",
                redisTemplate,
                JsonMapper.builder().build()
        );

        DomainException exception = assertThrows(
                DomainException.class,
                () -> adapter.computeRoute(new GeoPoint(11.24, -74.20), new GeoPoint(11.25, -74.21))
        );

        assertEquals("EXTERNAL_SERVICE_UNAVAILABLE", exception.code());
    }
}
