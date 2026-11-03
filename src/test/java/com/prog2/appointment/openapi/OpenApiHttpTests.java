package com.prog2.appointment.openapi;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.prog2.appointment.availability.domain.ports.in.GetAvailabilityUseCase;
import com.prog2.appointment.reservation.domain.model.OwnReservations;
import com.prog2.appointment.reservation.domain.ports.in.CancelReservationUseCase;
import com.prog2.appointment.reservation.domain.ports.in.GetReservationProcessUseCase;
import com.prog2.appointment.reservation.domain.ports.in.ListOwnReservationsUseCase;
import com.prog2.appointment.reservation.domain.ports.in.StartReservationUseCase;
import com.prog2.appointment.reservation.domain.ports.in.SubmitReservationPhoneUseCase;
import com.prog2.appointment.security.JwtTestTokens;
import com.prog2.appointment.user.domain.ports.in.AuthenticateUserUseCase;
import com.prog2.appointment.user.domain.ports.in.RegisterUserUseCase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = OpenApiTestConfiguration.class, properties = "API_DOCS_ENABLED=true")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("openapi-test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OpenApiHttpTests {
    private static final String DOCUMENT = "/v3/api-docs";
    private static final Map<String, String> OPERATIONS = Map.of(
            "/api/register", "post",
            "/api/authenticate", "post",
            "/api/appointment-availability", "get",
            "/api/reservations", "get",
            "/api/reservation-processes", "post",
            "/api/reservation-processes/{processId}", "get",
            "/api/reservation-processes/{processId}/phone", "post",
            "/api/reservation-processes/{processId}/cancel", "post");
    private final MockMvc mvc;
    private final RegisterUserUseCase registration;
    private final AuthenticateUserUseCase authentication;
    private final GetAvailabilityUseCase availability;
    private final StartReservationUseCase start;
    private final SubmitReservationPhoneUseCase phone;
    private final GetReservationProcessUseCase get;
    private final ListOwnReservationsUseCase list;
    private final CancelReservationUseCase cancel;

    OpenApiHttpTests(MockMvc mvc, RegisterUserUseCase registration,
            AuthenticateUserUseCase authentication, GetAvailabilityUseCase availability,
            StartReservationUseCase start, SubmitReservationPhoneUseCase phone,
            GetReservationProcessUseCase get, ListOwnReservationsUseCase list,
            CancelReservationUseCase cancel) {
        this.mvc = mvc;
        this.registration = registration;
        this.authentication = authentication;
        this.availability = availability;
        this.start = start;
        this.phone = phone;
        this.get = get;
        this.list = list;
        this.cancel = cancel;
    }

    @BeforeEach
    void fixtures() {
        when(list.list(42L, 0, 20)).thenReturn(new OwnReservations(null, List.of(), 0, 0, 20));
        clearInvocations(registration, authentication, availability, start, phone, get, list, cancel);
    }

    private JsonNode document() throws Exception {
        String json = mvc.perform(get(DOCUMENT)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonMapper.builder().build().readTree(json);
    }

    @Test
    void documentsExactlyTheEightExistingOperations() throws Exception {
        JsonNode paths = document().path("paths");
        assertThat(paths.propertyNames()).containsExactlyInAnyOrderElementsOf(OPERATIONS.keySet());
        OPERATIONS.forEach((path, method) -> {
            JsonNode item = paths.path(path);
            assertThat(item.propertyNames()).containsExactly(method);
            assertThat(item.path(method).path("responses").size()).isPositive();
        });
        assertThat(responseCodes(paths, "/api/register", "post"))
                .contains("201", "400", "503", "500");
        assertThat(responseCodes(paths, "/api/authenticate", "post"))
                .contains("200", "400", "401", "503", "500");
        assertThat(responseCodes(paths, "/api/appointment-availability", "get"))
                .contains("200", "400", "401", "403", "404", "503");
        assertThat(responseCodes(paths, "/api/reservations", "get"))
                .contains("200", "400", "401", "403", "503");
        assertThat(responseCodes(paths, "/api/reservation-processes", "post"))
                .contains("200", "202", "400", "401", "403", "409", "503");
        assertThat(responseCodes(paths, "/api/reservation-processes/{processId}", "get"))
                .contains("200", "401", "403", "404", "503");
        assertThat(responseCodes(paths, "/api/reservation-processes/{processId}/phone", "post"))
                .contains("200", "202", "400", "401", "403", "404", "409", "503");
        assertThat(responseCodes(paths, "/api/reservation-processes/{processId}/cancel", "post"))
                .contains("200", "202", "401", "403", "404", "409", "503");
    }

    @Test
    void usesOneGlobalBearerAndLeavesPublicOperationsAnonymous() throws Exception {
        JsonNode document = document();
        JsonNode schemes = document.path("components").path("securitySchemes");
        assertThat(schemes.propertyNames()).containsExactly("bearerAuth");
        JsonNode bearer = schemes.path("bearerAuth");
        assertThat(bearer.path("type").asString()).isEqualTo("http");
        assertThat(bearer.path("scheme").asString()).isEqualTo("bearer");
        assertThat(bearer.path("bearerFormat").asString()).isEqualTo("JWT");
        assertThat(document.path("security").size()).isEqualTo(1);
        assertThat(document.path("security").get(0).has("bearerAuth")).isTrue();
        for (String path : List.of("/api/register", "/api/authenticate")) {
            JsonNode security = document.path("paths").path(path).path("post").path("security");
            assertThat(security.isArray()).isTrue();
            assertThat(security.size()).isZero();
        }
    }

    @Test
    void documentsTheRealProblemSchemaAndJsonNames() throws Exception {
        JsonNode schemas = document().path("components").path("schemas");
        assertThat(schemas.path("ApiProblem").path("properties").propertyNames())
                .containsExactlyInAnyOrder("type", "title", "status", "code", "instance",
                        "processId", "recheckAt", "fieldErrors");
        assertThat(schemas.path("AuthenticationResponse").path("properties").propertyNames())
                .containsExactly("id_token");
        assertThat(schemas.path("ReservationProcessResponse").path("properties").propertyNames())
                .contains("state", "expiresAt", "cancellation", "startTime");
        assertThat(schemas.path("ReservationHistoryResponse").path("properties")
                .path("content").path("items").path("$ref").asString())
                .isEqualTo("#/components/schemas/ReservationProcessResponse");
    }

    @Test
    void documentsPageAndSizeAsIntegers() throws Exception {
        JsonNode parameters = document().path("paths").path("/api/reservations").path("get")
                .path("parameters");
        Map<String, JsonNode> byName = new HashMap<>();
        parameters.forEach(parameter -> byName.put(parameter.path("name").asString(), parameter));
        assertThat(byName).containsKeys("page", "size");
        JsonNode page = byName.get("page").path("schema");
        assertThat(page.path("type").asString()).isEqualTo("integer");
        assertThat(page.path("minimum").asInt(-1)).isZero();
        assertThat(page.path("default").asInt(-1)).isZero();
        JsonNode size = byName.get("size").path("schema");
        assertThat(size.path("type").asString()).isEqualTo("integer");
        assertThat(size.path("minimum").asInt()).isEqualTo(1);
        assertThat(size.path("maximum").asInt()).isEqualTo(100);
        assertThat(size.path("default").asInt()).isEqualTo(20);
    }

    @Test
    void servesUiAndYamlAnonymouslyWithoutCallingBusinessPorts() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        for (String path : new String[]{DOCUMENT, "/v3/api-docs.yaml", "/v3/api-docs/swagger-config",
                "/swagger-ui/index.html", "/swagger-ui/swagger-ui-bundle.js",
                "/swagger-ui/swagger-initializer.js"}) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(jsonPath("$.persistAuthorization").value(false))
                .andExpect(jsonPath("$.url").value(DOCUMENT));
        verifyNoInteractions(registration, authentication, availability, start, phone, get, list, cancel);
    }

    @Test
    void keepsApiPermissionsAfterDocumentIsReadAnonymously() throws Exception {
        document();
        when(list.list(42L, 0, 20)).thenReturn(new OwnReservations(null, List.of(), 0, 0, 20));
        String user = JwtTestTokens.signed(JwtTestTokens.claims());
        var otherClaims = JwtTestTokens.claims();
        otherClaims.put("auth", "ROLE_OTHER");
        String other = JwtTestTokens.signed(otherClaims);
        mvc.perform(get("/api/reservations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reservations").header(HttpHeaders.AUTHORIZATION, "Bearer " + user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history.content").isEmpty())
                .andExpect(jsonPath("$.history.page").value(0))
                .andExpect(jsonPath("$.history.size").value(20));
        mvc.perform(get("/api/reservations").header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
                .andExpect(status().isForbidden());
        verify(list).list(42L, 0, 20);
        verifyNoInteractions(registration, authentication, availability, start, phone, get, cancel);
    }

    @Test
    void anonymousDocumentationDoesNotOpenOtherMethodsOrSimilarPaths() throws Exception {
        String user = JwtTestTokens.signed(JwtTestTokens.claims());
        mvc.perform(post(DOCUMENT).header(HttpHeaders.AUTHORIZATION, "Bearer " + user))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v3/api-docs-extra").header(HttpHeaders.AUTHORIZATION, "Bearer " + user))
                .andExpect(status().isForbidden());
        mvc.perform(get("/swagger-ui-extra").header(HttpHeaders.AUTHORIZATION, "Bearer " + user))
                .andExpect(status().isForbidden());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        verifyNoInteractions(registration, authentication, availability, start, phone, get, list, cancel);
    }

    @Test
    void doesNotExposeConfigurationSecrets() throws Exception {
        String document = document().toString();
        assertThat(document).doesNotContain("fictional-group", "PRIVATE KEY", "private.pem");
    }

    private static Set<String> responseCodes(JsonNode paths, String path, String method) {
        Set<String> codes = new HashSet<>();
        paths.path(path).path(method).path("responses").propertyNames().forEach(codes::add);
        return codes;
    }
}
