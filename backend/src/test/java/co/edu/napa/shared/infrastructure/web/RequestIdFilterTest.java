package co.edu.napa.shared.infrastructure.web;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestIdFilterTest {
    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void preservesAValidRequestIdAndClearsMdcAfterRequest() throws Exception {
        String requestId = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER_NAME, requestId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertEquals(requestId, MDC.get(RequestIdFilter.MDC_KEY))
        );

        assertEquals(requestId, response.getHeader(RequestIdFilter.HEADER_NAME));
        assertEquals(requestId, request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME));
        assertNull(MDC.get(RequestIdFilter.MDC_KEY));
    }

    @Test
    void replacesAMalformedRequestIdWithANewUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER_NAME, "invalid\nrequest-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {});

        String requestId = response.getHeader(RequestIdFilter.HEADER_NAME);
        assertNotNull(requestId);
        assertEquals(requestId, UUID.fromString(requestId).toString());
    }
}
