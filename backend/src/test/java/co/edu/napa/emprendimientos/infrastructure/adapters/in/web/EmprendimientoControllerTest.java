package co.edu.napa.emprendimientos.infrastructure.adapters.in.web;

import co.edu.napa.cuentas.domain.model.RolUsuario;
import co.edu.napa.emprendimientos.application.ports.in.CrearEmprendimientoPort;
import co.edu.napa.emprendimientos.application.ports.in.ListarEmprendimientosPort;
import co.edu.napa.emprendimientos.application.ports.out.CampoOrdenEmprendimientos;
import co.edu.napa.emprendimientos.application.ports.out.DireccionOrdenEmprendimientos;
import co.edu.napa.emprendimientos.application.ports.out.FiltroEmprendimientos;
import co.edu.napa.emprendimientos.application.ports.in.CrearEmprendimientoCommand;
import co.edu.napa.emprendimientos.application.ports.in.CrearEmprendimientoResult;
import co.edu.napa.emprendimientos.application.ports.in.EmprendimientosPagina;
import co.edu.napa.emprendimientos.application.ports.in.ListarEmprendimientosQuery;
import co.edu.napa.emprendimientos.domain.model.CategoriaEmprendimiento;
import co.edu.napa.emprendimientos.domain.model.Emprendimiento;
import co.edu.napa.emprendimientos.domain.model.EstadoEmprendimiento;
import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.domain.geo.Ubicacion;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmprendimientoControllerTest {
    private static final UUID USER_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final UUID BUSINESS_ID = UUID.fromString("60000000-0000-0000-0000-000000000002");
    private static final UUID LOCATION_ID = UUID.fromString("60000000-0000-0000-0000-000000000003");
    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");

    @Mock private CrearEmprendimientoPort crearEmprendimiento;
    @Mock private ListarEmprendimientosPort listarEmprendimientos;

    private EmprendimientoController controller;

    @BeforeEach
    void setUp() {
        controller = new EmprendimientoController(crearEmprendimiento, listarEmprendimientos);
    }

    @Test
    void parsesListingFiltersAndReturnsThePagedResult() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("tipoVendedor", "AMBULANTE");
        request.addParameter("lat", "11.24");
        request.addParameter("lng", "-74.21");
        request.addParameter("radioMetros", "2000");
        request.addParameter("orden", "cercania");
        request.addParameter("page", "1");
        request.addParameter("size", "10");
        EmprendimientosPagina page = new EmprendimientosPagina(List.of(), 1, 10, 0);
        when(listarEmprendimientos.listar(any())).thenReturn(page);

        var response = controller.listar(request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(page, response.getBody());
        verify(listarEmprendimientos).listar(new ListarEmprendimientosQuery(
                new FiltroEmprendimientos(
                        co.edu.napa.emprendimientos.domain.model.TipoVendedor.AMBULANTE,
                        11.24,
                        -74.21,
                        2000,
                        true,
                        CampoOrdenEmprendimientos.CREATED_AT,
                        DireccionOrdenEmprendimientos.DESC
                ),
                1,
                10
        ));
    }

    @Test
    void rejectsUnknownListingFilters() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("tipoEntidad", "EMPRENDIMIENTO");

        DomainException exception = assertThrows(DomainException.class, () -> controller.listar(request));

        assertEquals("VALIDATION_ERROR", exception.code());
        verify(listarEmprendimientos, never()).listar(any());
    }

    @Test
    void rejectsMalformedListingNumbersAndUnsupportedOrdering() {
        MockHttpServletRequest malformedNumber = new MockHttpServletRequest();
        malformedNumber.addParameter("lat", "norte");
        DomainException numberException = assertThrows(
                DomainException.class,
                () -> controller.listar(malformedNumber)
        );
        assertEquals("VALIDATION_ERROR", numberException.code());

        MockHttpServletRequest invalidOrder = new MockHttpServletRequest();
        invalidOrder.addParameter("orden", "popularidad");
        DomainException orderException = assertThrows(
                DomainException.class,
                () -> controller.listar(invalidOrder)
        );
        assertEquals("VALIDATION_ERROR", orderException.code());

        verify(listarEmprendimientos, never()).listar(any());
    }

    @Test
    void createsBusinessUsingRoleAndOwnerFromTheAuthenticatedJwt() {
        Emprendimiento business = new Emprendimiento(
                BUSINESS_ID,
                USER_ID,
                LOCATION_ID,
                "Frutas Doña Rosa",
                CategoriaEmprendimiento.FRUTAS_VERDURAS,
                "Frutas frescas",
                LocalTime.of(6, 0),
                LocalTime.of(18, 0),
                EstadoEmprendimiento.ACTIVO,
                NOW,
                NOW
        );
        Ubicacion location = new Ubicacion(
                LOCATION_ID,
                11.2408,
                -74.211,
                null,
                "Frente al parque",
                NOW,
                NOW
        );
        when(crearEmprendimiento.crear(any()))
                .thenReturn(new CrearEmprendimientoResult(
                        business,
                        location,
                        RolUsuario.VENDEDOR_AMBULANTE
                ));

        var response = controller.crear(
                jwt(RolUsuario.VENDEDOR_AMBULANTE),
                new EmprendimientoController.CrearEmprendimientoRequest(
                        "Frutas Doña Rosa",
                        CategoriaEmprendimiento.FRUTAS_VERDURAS,
                        "Frutas frescas",
                        LocalTime.of(6, 0),
                        LocalTime.of(18, 0),
                        new EmprendimientoController.UbicacionRequest(
                                11.2408,
                                -74.211,
                                null,
                                "Frente al parque"
                        )
                ),
                new MockHttpServletRequest()
        );

        assertEquals(201, response.getStatusCode().value());
        assertEquals("/api/v1/emprendimientos/" + BUSINESS_ID, response.getHeaders().getLocation().toString());
        assertEquals(RolUsuario.VENDEDOR_AMBULANTE, response.getBody().tipoVendedor());
        assertNull(response.getBody().ubicacion().direccion());

        ArgumentCaptor<CrearEmprendimientoCommand> command =
                ArgumentCaptor.forClass(CrearEmprendimientoCommand.class);
        verify(crearEmprendimiento).crear(command.capture());
        assertEquals(USER_ID, command.getValue().usuarioId());
        assertEquals(RolUsuario.VENDEDOR_AMBULANTE, command.getValue().rolUsuario());
    }

    @Test
    void rejectsQueryParametersNotDefinedByTheContract() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("usuarioId", USER_ID.toString());

        DomainException exception = assertThrows(
                DomainException.class,
                () -> controller.crear(
                        jwt(RolUsuario.VENDEDOR_PUNTO_FIJO),
                        new EmprendimientoController.CrearEmprendimientoRequest(
                                "Tienda",
                                CategoriaEmprendimiento.OTROS,
                                "Descripción",
                                LocalTime.of(8, 0),
                                LocalTime.of(17, 0),
                                new EmprendimientoController.UbicacionRequest(0.0, 0.0, "Calle 1", null)
                        ),
                        request
                )
        );

        assertEquals("VALIDATION_ERROR", exception.code());
    }

    @Test
    void requestValidationRejectsMissingFieldsAndOutOfRangeCoordinates() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var request = new EmprendimientoController.CrearEmprendimientoRequest(
                    " ",
                    null,
                    "",
                    null,
                    null,
                    new EmprendimientoController.UbicacionRequest(91.0, 181.0, null, null)
            );
            Set<String> invalidFields = factory.getValidator().validate(request).stream()
                    .map(violation -> violation.getPropertyPath().toString())
                    .collect(Collectors.toSet());

            assertTrue(invalidFields.containsAll(Set.of(
                    "nombre",
                    "categoria",
                    "descripcion",
                    "horaApertura",
                    "horaCierre",
                    "ubicacion.latitud",
                    "ubicacion.longitud"
            )));
        }
    }

    @Test
    void methodSecurityDeniesNonSellerRolesBeforeInvokingTheUseCase() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(CrearEmprendimientoPort.class, () -> crearEmprendimiento);
            context.registerBean(
                    EmprendimientoController.class,
                    () -> new EmprendimientoController(crearEmprendimiento, listarEmprendimientos)
            );
            context.register(MethodSecurityTestConfiguration.class);
            context.refresh();
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    USER_ID.toString(),
                    "unused",
                    List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))
            ));

            EmprendimientoController securedController = context.getBean(EmprendimientoController.class);
            assertThrows(
                    AccessDeniedException.class,
                    () -> securedController.crear(
                            jwt(RolUsuario.CLIENTE),
                            new EmprendimientoController.CrearEmprendimientoRequest(
                                    "Tienda",
                                    CategoriaEmprendimiento.OTROS,
                                    "Descripción",
                                    LocalTime.of(8, 0),
                                    LocalTime.of(17, 0),
                                    new EmprendimientoController.UbicacionRequest(0.0, 0.0, null, null)
                            ),
                            new MockHttpServletRequest()
                    )
            );
        } finally {
            SecurityContextHolder.clearContext();
        }
        verify(crearEmprendimiento, never()).crear(any());
    }

    @Test
    void listingRequiresClientRoleBeforeInvokingTheUseCase() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(CrearEmprendimientoPort.class, () -> crearEmprendimiento);
            context.registerBean(ListarEmprendimientosPort.class, () -> listarEmprendimientos);
            context.registerBean(
                    EmprendimientoController.class,
                    () -> new EmprendimientoController(crearEmprendimiento, listarEmprendimientos)
            );
            context.register(MethodSecurityTestConfiguration.class);
            context.refresh();
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    USER_ID.toString(),
                    "unused",
                    List.of(new SimpleGrantedAuthority("ROLE_VENDEDOR_AMBULANTE"))
            ));

            EmprendimientoController securedController = context.getBean(EmprendimientoController.class);
            assertThrows(
                    AccessDeniedException.class,
                    () -> securedController.listar(new MockHttpServletRequest())
            );
        } finally {
            SecurityContextHolder.clearContext();
        }
        verify(listarEmprendimientos, never()).listar(any());
    }

    private Jwt jwt(RolUsuario rol) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(USER_ID.toString())
                .claim("rol", rol.name())
                .build();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityTestConfiguration {
    }
}
