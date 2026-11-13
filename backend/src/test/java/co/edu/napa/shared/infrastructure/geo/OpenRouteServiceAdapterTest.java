package co.edu.napa.shared.infrastructure.geo;

import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.domain.geo.GeoPoint;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenRouteServiceAdapterTest {
    private static final String BASE_URL = "https://api.openrouteservice.org";

    private final GeoPoint origin = new GeoPoint(11.24, -74.20);
    private final GeoPoint destination = new GeoPoint(11.25, -74.21);

    @Test
    void computesRouteSendsLongitudeFirstAndCachesResponse() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = valueOperations(redisTemplate);
        when(values.get(anyString())).thenReturn(null);

        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/v2/directions/driving-car"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "test-key"))
                .andExpect(jsonPath("$.coordinates[0][0]").value(-74.20))
                .andExpect(jsonPath("$.coordinates[0][1]").value(11.24))
                .andExpect(jsonPath("$.coordinates[1][0]").value(-74.21))
                .andExpect(jsonPath("$.coordinates[1][1]").value(11.25))
                .andRespond(withSuccess(
                        """
                        {
                          "routes": [{
                            "summary": {"distance": 1239.6, "duration": 83.7},
                            "geometry": "encoded-route",
                            "way_points": [0, 12],
                            "bbox": [-74.21, 11.24, -74.20, 11.25]
                          }],
                          "metadata": {"service": "routing"}
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        var result = adapter(builder.build(), "test-key", redisTemplate)
                .computeRoute(origin, destination);

        assertEquals(1240, result.distanceMeters());
        assertEquals(Duration.ofSeconds(84), result.duration());
        assertEquals("encoded-route", result.encodedPolyline());
        verify(values).set(anyString(), anyString(), eq(Duration.ofHours(24)));
        server.verify();
    }

    @Test
    void returnsCachedRouteWithoutCallingTheService() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = valueOperations(redisTemplate);
        when(values.get(anyString())).thenReturn(
                "{\"distanceMeters\":500,\"durationSeconds\":60,\"encodedPolyline\":\"cached\"}"
        );

        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        var result = adapter(builder.build(), "test-key", redisTemplate)
                .computeRoute(origin, destination);

        assertEquals(500, result.distanceMeters());
        assertEquals("cached", result.encodedPolyline());
        server.verify();
    }

    @Test
    void reportsMissingApiKeyAsExternalServiceUnavailable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = valueOperations(redisTemplate);
        when(values.get(anyString())).thenReturn(null);

        var adapter = adapter(RestClient.builder().baseUrl(BASE_URL).build(), "", redisTemplate);

        DomainException exception = assertThrows(
                DomainException.class,
                () -> adapter.computeRoute(origin, destination)
        );

        assertEquals("EXTERNAL_SERVICE_UNAVAILABLE", exception.code());
    }

    @Test
    void reportsExhaustedQuotaAsExternalServiceUnavailable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = valueOperations(redisTemplate);
        when(values.get(anyString())).thenReturn(null);

        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/v2/directions/driving-car"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        var adapter = adapter(builder.build(), "test-key", redisTemplate);

        DomainException exception = assertThrows(
                DomainException.class,
                () -> adapter.computeRoute(origin, destination)
        );

        assertEquals("EXTERNAL_SERVICE_UNAVAILABLE", exception.code());
        server.verify();
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> valueOperations(StringRedisTemplate redisTemplate) {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        return values;
    }

    private OpenRouteServiceAdapter adapter(
            RestClient restClient,
            String apiKey,
            StringRedisTemplate redisTemplate
    ) {
        return new OpenRouteServiceAdapter(
                restClient,
                apiKey,
                redisTemplate,
                JsonMapper.builder().build()
        );
    }
}