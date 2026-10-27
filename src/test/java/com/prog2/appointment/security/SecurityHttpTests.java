package com.prog2.appointment.security;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.prog2.appointment.infrastructure.config.TechnicalAccessProperties;
import com.prog2.appointment.infrastructure.security.config.SecurityConfiguration;
import com.prog2.appointment.integration.TestIntegrationSupport;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Filtros, firma y autorización reales; las operaciones observadas existen solo en este contexto de test. */
@WebMvcTest(controllers = SecurityHttpTests.ProbeController.class,
        properties = "CORS_ALLOWED_ORIGINS=https://app.test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({SecurityConfiguration.class, SecurityHttpTests.Fixtures.class, SecurityHttpTests.ProbeController.class})
@ExtendWith(OutputCaptureExtension.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class SecurityHttpTests {
    static final String IDENTITY = "/api/security-test/identity";
    private final MockMvc mvc;
    private final ProbeController probe;

    /** Spring/JUnit entrega dependencias al constructor, sin Autowired ni inyección por campos. */
    SecurityHttpTests(MockMvc mvc, ProbeController probe) {
        this.mvc = mvc;
        this.probe = probe;
    }

    @BeforeEach
    void resetProbe() {
        probe.executions.set(0);
        probe.barrier.set(null);
    }

    @Test
    void acceptsRealSignatureAndMapsOnlyAgreedAuthorities() throws Exception {
        var claims = JwtTestTokens.claims();
        claims.put("auth", "ROLE_USER ROLE_OTHER");
        var result = bearer(JwtTestTokens.signed(claims))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subject").value("42"))
                .andExpect(jsonPath("$.authorities").value(hasItems("ROLE_OTHER", "ROLE_USER")))
                .andExpect(jsonPath("$.session").value(false))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE)).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(probe.executions).hasValue(1);
        problem(mvc.perform(get(IDENTITY)), 401, "AUTHENTICATION_REQUIRED");
        assertThat(probe.executions).hasValue(1);
    }

    @Test
    void rejectsMissingMalformedAndAmbiguousCredentialsWithoutExecuting() throws Exception {
        problem(mvc.perform(get(IDENTITY)), 401, "AUTHENTICATION_REQUIRED");
        problem(bearer("fictional-invalid-bearer"), 401, "AUTHENTICATION_REQUIRED");
        problem(bearer("first second"), 401, "AUTHENTICATION_REQUIRED");
        String token = JwtTestTokens.signed(JwtTestTokens.claims());
        problem(mvc.perform(get(IDENTITY).param("access_token", token)), 401, "AUTHENTICATION_REQUIRED");
        problem(mvc.perform(post(IDENTITY).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("access_token", token)), 401, "AUTHENTICATION_REQUIRED");
        problem(mvc.perform(get(IDENTITY).cookie(new jakarta.servlet.http.Cookie("access_token", token))),
                401, "AUTHENTICATION_REQUIRED");
        problem(mvc.perform(get(IDENTITY).header(HttpHeaders.AUTHORIZATION, "Basic ZmljdGlvbmFsOnRlc3Q=")),
                401, "AUTHENTICATION_REQUIRED");
        assertThat(probe.executions).hasValue(0);
    }

    @Test
    void rejectsOtherKeysTamperedPayloadAndOtherAlgorithms() throws Exception {
        var claims = JwtTestTokens.claims();
        problem(bearer(JwtTestTokens.signed(claims, TestIntegrationSupport.generateKeys(2048), JWSAlgorithm.RS256)),
                401, "AUTHENTICATION_REQUIRED");
        String token = JwtTestTokens.signed(claims);
        var altered = new JWSObject(new JWSHeader(JWSAlgorithm.RS256), new Payload(Map.of("sub", "99")));
        String tampered = token.split("\\.")[0] + "." + altered.getPayload().toBase64URL() + "." + token.split("\\.")[2];
        problem(bearer(tampered), 401, "AUTHENTICATION_REQUIRED");
        problem(bearer(new PlainJWT(JWTClaimsSet.parse(claims)).serialize()), 401, "AUTHENTICATION_REQUIRED");
        problem(bearer(JwtTestTokens.signed(claims, JwtTestTokens.KEYS, JWSAlgorithm.RS512)),
                401, "AUTHENTICATION_REQUIRED");
        var hmac = new JWSObject(new JWSHeader(JWSAlgorithm.HS512), new Payload(claims));
        hmac.sign(new MACSigner(JwtTestTokens.KEYS.getPublic().getEncoded()));
        problem(bearer(hmac.serialize()), 401, "AUTHENTICATION_REQUIRED");
        assertThat(probe.executions).hasValue(0);
    }

    static Stream<Arguments> invalidClaims() {
        return Stream.of(
                Arguments.of("iss", null), Arguments.of("iss", "other-service"),
                Arguments.of("aud", null), Arguments.of("aud", List.of("catalog")),
                Arguments.of("sub", null), Arguments.of("sub", ""), Arguments.of("sub", " "),
                Arguments.of("sub", "0"), Arguments.of("sub", "-1"), Arguments.of("sub", "01"),
                Arguments.of("sub", "patient"), Arguments.of("sub", "9223372036854775808"),
                Arguments.of("sub", List.of("42")),
                Arguments.of("token_use", null), Arguments.of("token_use", "service"),
                Arguments.of("token_use", "technical"), Arguments.of("token_use", 7),
                Arguments.of("auth", null), Arguments.of("auth", List.of("ROLE_USER")),
                Arguments.of("auth", "ROLE_USER,ROLE_OTHER"), Arguments.of("auth", "ROLE_USER  ROLE_OTHER"),
                Arguments.of("auth", "ROLE_USER "), Arguments.of("auth", 7),
                Arguments.of("iat", null), Arguments.of("exp", null),
                Arguments.of("iat", JwtTestTokens.NOW.plusSeconds(31).getEpochSecond()),
                Arguments.of("exp", JwtTestTokens.NOW.getEpochSecond()),
                Arguments.of("exp", JwtTestTokens.NOW.minusSeconds(1).getEpochSecond()),
                Arguments.of("iat", "fictional-invalid-time"));
    }

    @ParameterizedTest(name = "claim incompatible: {0} = {1}")
    @MethodSource("invalidClaims")
    void rejectsSignedTokensOutsideContract(String name, Object value) throws Exception {
        var claims = JwtTestTokens.claims();
        if (value == null) {
            claims.remove(name);
        } else {
            claims.put(name, value);
        }
        problem(bearer(JwtTestTokens.signed(claims)), 401, "AUTHENTICATION_REQUIRED");
        assertThat(probe.executions).hasValue(0);
    }

    static Stream<Arguments> timeBoundaries() {
        return Stream.of(Arguments.of(-30, 200), Arguments.of(-31, 401), Arguments.of(30, 200), Arguments.of(31, 401));
    }

    @ParameterizedTest
    @MethodSource("timeBoundaries")
    void checksExpiryAndNotBeforeWithExactClockTolerance(long offset, int expected) throws Exception {
        var claims = JwtTestTokens.claims();
        if (offset < 0) {
            claims.put("iat", JwtTestTokens.NOW.minusSeconds(86400).getEpochSecond());
            claims.put("exp", JwtTestTokens.NOW.plusSeconds(offset).getEpochSecond());
        } else {
            claims.put("nbf", JwtTestTokens.NOW.plusSeconds(offset).getEpochSecond());
        }
        bearer(JwtTestTokens.signed(claims)).andExpect(status().is(expected));
        assertThat(probe.executions).hasValue(expected == 200 ? 1 : 0);
    }

    @Test
    void acceptsIssuanceAtToleranceAndThirtyDayUserLifetime() throws Exception {
        var claims = JwtTestTokens.claims();
        claims.put("iat", JwtTestTokens.NOW.plusSeconds(30).getEpochSecond());
        claims.put("exp", JwtTestTokens.NOW.plusSeconds(30L * 86400).getEpochSecond());
        bearer(JwtTestTokens.signed(claims)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ROLE_OTHER", "ROLE_CATALOG_INTEGRATION"})
    void admittedIdentityWithoutUserPermissionIsForbidden(String roles) throws Exception {
        var claims = JwtTestTokens.claims();
        claims.put("auth", roles);
        problem(bearer(JwtTestTokens.signed(claims)), 403, "ACCESS_DENIED");
        assertThat(probe.executions).hasValue(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/internal/catedra/token", "/error", "/actuator/env", "/actuator"})
    void doesNotOpenInternalOrUnlistedRoutesForUsers(String path) throws Exception {
        problem(mvc.perform(get(path)), 401, "AUTHENTICATION_REQUIRED");
        String token = JwtTestTokens.signed(JwtTestTokens.claims());
        problem(mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)), 403, "ACCESS_DENIED");
    }

    @Test
    void clientIdentifiersAndRolesCannotChangeAuthenticatedIdentity() throws Exception {
        String token = JwtTestTokens.signed(JwtTestTokens.claims());
        mvc.perform(post(IDENTITY).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-User-Id", "99").header("X-Authorities", "ROLE_ADMIN")
                        .param("userId", "99").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":99,\"authorities\":[\"ROLE_ADMIN\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subject").value("42"))
                .andExpect(jsonPath("$.authorities").value(hasItems("ROLE_USER")))
                .andExpect(jsonPath("$.authorities").value(not(hasItem("ROLE_ADMIN"))));
    }

    @Test
    void simultaneousRequestsKeepDifferentIdentitiesAndLeaveNoAnonymousSession() throws Exception {
        var firstClaims = JwtTestTokens.claims();
        var secondClaims = JwtTestTokens.claims();
        secondClaims.put("sub", "99");
        secondClaims.put("auth", "ROLE_USER ROLE_OTHER");
        String first = JwtTestTokens.signed(firstClaims);
        String second = JwtTestTokens.signed(secondClaims);
        probe.barrier.set(new CyclicBarrier(2));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstRequest = executor.submit(() -> bearer(first).andExpect(status().isOk())
                    .andExpect(jsonPath("$.subject").value("42"))
                    .andExpect(jsonPath("$.authorities").value(hasItems("ROLE_USER")))
                    .andExpect(jsonPath("$.authorities").value(not(hasItem("ROLE_OTHER")))));
            var secondRequest = executor.submit(() -> bearer(second).andExpect(status().isOk())
                    .andExpect(jsonPath("$.subject").value("99"))
                    .andExpect(jsonPath("$.authorities").value(hasItems("ROLE_OTHER", "ROLE_USER"))));
            firstRequest.get(10, TimeUnit.SECONDS);
            secondRequest.get(10, TimeUnit.SECONDS);
        } finally {
            probe.barrier.set(null);
        }
        assertThat(probe.executions).hasValue(2);
        problem(mvc.perform(get(IDENTITY)), 401, "AUTHENTICATION_REQUIRED");
        assertThat(probe.executions).hasValue(2);
    }

    @Test
    void permitsOnlyConfiguredCorsWithoutGrantingUserPermissions() throws Exception {
        mvc.perform(options(IDENTITY).header(HttpHeaders.ORIGIN, "https://app.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type"))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.test"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        problem(mvc.perform(post(IDENTITY).header(HttpHeaders.ORIGIN, "https://app.test")), 401, "AUTHENTICATION_REQUIRED");
        mvc.perform(options(IDENTITY).header(HttpHeaders.ORIGIN, "https://other.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        mvc.perform(options(IDENTITY).header(HttpHeaders.ORIGIN, "https://app.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE"))
                .andExpect(status().isForbidden());
        assertThat(probe.executions).hasValue(0);
    }

    @Test
    void errorsDoNotEchoBearerOrWriteItsValueToLogs(CapturedOutput output) throws Exception {
        String marker = "fictional-sensitive-invalid-bearer";
        var result = problem(bearer(marker), 401, "AUTHENTICATION_REQUIRED").andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(marker, "exception", "trace");
        assertThat(output).doesNotContain(marker);
    }

    private ResultActions bearer(String token) throws Exception {
        return mvc.perform(get(IDENTITY).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static ResultActions problem(ResultActions response, int code, String error) throws Exception {
        return response.andExpect(status().is(code))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(code)).andExpect(jsonPath("$.code").value(error))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Fixtures {
        @Bean
        TechnicalAccessProperties technicalAccessProperties() {
            return JwtTestTokens.access();
        }

        @Bean
        Clock clock() {
            return Clock.fixed(JwtTestTokens.NOW, ZoneOffset.UTC);
        }
    }

    /** Observa el principal real sin introducir una API en el artefacto productivo. */
    @RestController
    static class ProbeController {
        final AtomicInteger executions = new AtomicInteger();
        final AtomicReference<CyclicBarrier> barrier = new AtomicReference<>();

        @RequestMapping(path = IDENTITY, method = {RequestMethod.GET, RequestMethod.POST})
        Map<String, Object> identity(JwtAuthenticationToken authentication, HttpServletRequest request)
                throws InterruptedException, java.util.concurrent.BrokenBarrierException,
                java.util.concurrent.TimeoutException {
            executions.incrementAndGet();
            CyclicBarrier current = barrier.get();
            if (current != null) {
                current.await(5, TimeUnit.SECONDS);
            }
            return Map.of("subject", authentication.getName(), "authorities", authentication.getAuthorities().stream()
                    .map(org.springframework.security.core.GrantedAuthority::getAuthority).sorted().toList(),
                    "session", request.getSession(false) != null);
        }
    }
}
