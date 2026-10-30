package com.prog2.appointment.availability;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.prog2.appointment.integration.TestIntegrationSupport;

/** Dos destinos ficticios para adquisición y CU-03; rechaza escrituras y verifica credenciales por proveedor. */
public final class AvailabilityTestSupport implements AutoCloseable {
    public static final Instant NOW = Instant.parse("2026-11-02T11:00:00Z");
    public static final LocalDate DATE = LocalDate.of(2026, 11, 2);
    private final HttpServer catalog;
    private final HttpServer central;
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final Path privatePath;
    public final String centralToken = TestIntegrationSupport.token(NOW.plusSeconds(172800));
    private final AtomicReference<String> issuedToken = new AtomicReference<>(centralToken);
    public final AtomicInteger agendaCalls = new AtomicInteger();
    public final AtomicInteger occupancyCalls = new AtomicInteger();
    public final AtomicInteger tokenCalls = new AtomicInteger();
    public final AtomicInteger integrationCalls = new AtomicInteger();
    public final AtomicInteger unexpectedCalls = new AtomicInteger();
    public final AtomicInteger invalidCredentials = new AtomicInteger();
    public final AtomicInteger agendaStatus = new AtomicInteger(200);
    public final AtomicInteger occupancyStatus = new AtomicInteger(200);
    public final AtomicReference<String> agendaBody = new AtomicReference<>();
    public final AtomicReference<String> occupancyBody = new AtomicReference<>();
    public final AtomicReference<Handler> agendaHandler = new AtomicReference<>();
    public final AtomicReference<Handler> occupancyHandler = new AtomicReference<>();
    public final AtomicReference<String> lastOccupancyQuery = new AtomicReference<>();

    public AvailabilityTestSupport() {
        try {
            privatePath = TestIntegrationSupport.writePrivateKey(TestIntegrationSupport.KEYS);
            catalog = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            central = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            catalog.setExecutor(workers);
            central.setExecutor(workers);
            catalog.createContext("/", this::catalog);
            central.createContext("/", this::central);
            catalog.start();
            central.start();
        } catch (IOException failure) {
            workers.shutdownNow();
            throw new IllegalStateException("Cannot start fictional availability providers", failure);
        }
    }

    public URI catalogUrl() {
        return URI.create("http://127.0.0.1:" + catalog.getAddress().getPort());
    }

    public URI centralUrl() {
        return URI.create("http://127.0.0.1:" + central.getAddress().getPort());
    }

    /** @return configuración ficticia para Boot; nunca usa .env o una privada personal */
    public Map<String, String> settings() {
        return Map.of("CATALOG_BASE_URL", catalogUrl().toString(), "CATEDRA_BASE_URL", centralUrl().toString(),
                "CATEDRA_GROUP_ID", TestIntegrationSupport.GROUP, "JWT_PRIVATE_KEY_PATH", privatePath.toString());
    }

    private void catalog(HttpExchange exchange) throws IOException {
        if (!getOnly(exchange) || !serviceBearer(exchange)) {
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if ("/api/internal/catedra/token".equals(path)) {
            tokenCalls.incrementAndGet();
            TestIntegrationSupport.respond(exchange, 200, "{\"idToken\":\"" + issuedToken.get() + "\"}");
        } else if (path.startsWith("/api/internal/catalog/professionals/")) {
            agendaCalls.incrementAndGet();
            Handler handler = agendaHandler.get();
            if (handler != null) {
                handler.handle(exchange);
                return;
            }
            long id = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
            String body = agendaBody.get();
            TestIntegrationSupport.respond(exchange, agendaStatus.get(), body == null ? agenda(id, true) : body);
        } else {
            unexpected(exchange);
        }
    }

    private void central(HttpExchange exchange) throws IOException {
        if (!getOnly(exchange)) {
            return;
        }
        if (!("Bearer " + issuedToken.get()).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            invalidCredentials.incrementAndGet();
            TestIntegrationSupport.respond(exchange, 401, "{}");
            return;
        }
        if ("/api/student/integration".equals(exchange.getRequestURI().getPath())) {
            integrationCalls.incrementAndGet();
            TestIntegrationSupport.respond(exchange, 200, TestIntegrationSupport.configuration("PROVISIONED"));
        } else if ("/api/appointment-occupancies".equals(exchange.getRequestURI().getPath())) {
            occupancyCalls.incrementAndGet();
            lastOccupancyQuery.set(exchange.getRequestURI().getRawQuery());
            Handler handler = occupancyHandler.get();
            if (handler != null) {
                handler.handle(exchange);
                return;
            }
            var query = query(exchange);
            String body = occupancyBody.get();
            TestIntegrationSupport.respond(exchange, occupancyStatus.get(), body == null
                    ? occupancies(Long.parseLong(query.get("professionalId")), LocalDate.parse(query.get("from")), "") : body);
        } else {
            unexpected(exchange);
        }
    }

    private boolean getOnly(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            unexpected(exchange);
            return false;
        }
        return true;
    }

    private boolean serviceBearer(HttpExchange exchange) throws IOException {
        try {
            String header = exchange.getRequestHeaders().getFirst("Authorization");
            SignedJWT jwt = SignedJWT.parse(header.substring("Bearer ".length()));
            var claims = jwt.getJWTClaimsSet();
            if (jwt.verify(new RSASSAVerifier((RSAPublicKey) TestIntegrationSupport.KEYS.getPublic()))
                    && JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())
                    && "backend-turnos".equals(claims.getIssuer())
                    && "backend-turnos".equals(claims.getSubject())
                    && claims.getAudience().contains("catalog")
                    && "service".equals(claims.getStringClaim("token_use"))
                    && "ROLE_CATALOG_INTEGRATION".equals(claims.getStringClaim("auth"))
                    && claims.getExpirationTime().toInstant().equals(claims.getIssueTime().toInstant().plusSeconds(300))) {
                return true;
            }
        } catch (java.text.ParseException | JOSEException | RuntimeException _) {
            // Solo se cuenta el rechazo; una cabecera nunca se imprime al fallar el fixture.
        }
        invalidCredentials.incrementAndGet();
        TestIntegrationSupport.respond(exchange, 401, "{}");
        return false;
    }

    private void unexpected(HttpExchange exchange) throws IOException {
        unexpectedCalls.incrementAndGet();
        TestIntegrationSupport.respond(exchange, 404, "{}");
    }

    public void resetQueries() {
        agendaCalls.set(0);
        occupancyCalls.set(0);
        unexpectedCalls.set(0);
        invalidCredentials.set(0);
        agendaStatus.set(200);
        occupancyStatus.set(200);
        agendaBody.set(null);
        occupancyBody.set(null);
        agendaHandler.set(null);
        occupancyHandler.set(null);
    }

    /** Cambia únicamente el proveedor ficticio para probar readquisición de una sesión distinta. */
    public void rotateCentralToken() {
        issuedToken.set(TestIntegrationSupport.token(NOW.plusSeconds(259200)));
    }

    public static Map<String, String> query(HttpExchange exchange) {
        return java.util.Arrays.stream(exchange.getRequestURI().getRawQuery().split("&"))
                .map(value -> value.split("=", 2))
                .collect(java.util.stream.Collectors.toMap(value -> value[0], value -> value[1]));
    }

    public static String agenda(long id, boolean enabled) {
        return """
                {"professional":{"id":%d,"enabled":%s,"firstName":"Ficticio"},
                 "weeklySchedules":[{"professionalId":%d,"dayOfWeek":"MONDAY","startTime":"09:00",
                  "endTime":"11:00:00","slotDurationMinutes":30,"enabled":true}],
                 "catalog":{"state":"CURRENT","copyAvailable":true,"appliedVersion":7,"observedVersion":7,
                  "lastCheckedAt":"2026-11-02T10:59:59Z","evaluatedAt":"2026-11-02T11:00:00Z"},
                 "futureField":true}
                """.formatted(id, enabled, id);
    }

    public static String occupancies(long id, LocalDate date, String entries) {
        return """
                {"professionalId":%d,"from":"%s","to":"%s","occupancies":[%s],"futureField":true}
                """.formatted(id, date, date, entries);
    }

    public static String position(LocalDate date, String start, String status, String expires) {
        String expiration = expires == null ? "null" : "\"" + expires + "\"";
        return """
                {"date":"%s","startTime":"%s","status":"%s","expiresAt":%s}
                """.formatted(date, start, status, expiration).strip();
    }

    @Override
    public void close() {
        catalog.stop(0);
        central.stop(0);
        workers.shutdownNow();
        try {
            Files.deleteIfExists(privatePath);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot remove fictional private key", failure);
        }
    }

    /** Acción HTTP controlada para cortes, barreras y cuerpos incompletos. */
    @FunctionalInterface
    public interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
