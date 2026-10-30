package com.prog2.appointment.reservation;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.prog2.appointment.integration.TestIntegrationSupport;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.json.JsonMapper;

/** Cátedra/Catálogo ficticios; el broker se suministra desde infraestructura descartable. */
public final class ReservationTestSupport implements AutoCloseable {
    public static final String GROUP = "reservation-test";
    public static final String TOPIC = "reservation-test-actions";
    public static final String CONSUMER = "reservation-test-consumer";
    public final String token =
            TestIntegrationSupport.token(ReservationProcessTests.NOW.plusSeconds(86400));
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    private final HttpServer catalog;
    private final HttpServer central;
    private final ExecutorService executor =
            Executors.newCachedThreadPool(
                    task -> {
                        var thread = new Thread(task, "fictional-reservation-provider");
                        thread.setDaemon(true);
                        return thread;
                    });
    private final Path key;
    private final Supplier<String> broker;
    public final AtomicInteger creates = new AtomicInteger();
    public final AtomicInteger confirmations = new AtomicInteger();
    public final AtomicInteger credentialFailures = new AtomicInteger();
    public final AtomicInteger occupancyQueries = new AtomicInteger();
    public final AtomicReference<HttpHandler> occupanciesOverride = new AtomicReference<>();
    public final AtomicBoolean dropCreation = new AtomicBoolean();
    public final AtomicInteger creationStatus = new AtomicInteger(201);
    public final AtomicInteger confirmationStatus = new AtomicInteger(202);
    public final AtomicReference<Hold> last = new AtomicReference<>();
    public final AtomicReference<String> submittedFirstName = new AtomicReference<>();
    public final AtomicReference<String> submittedPatient = new AtomicReference<>();
    public final AtomicReference<Consumer<Hold>> beforeConfirm = new AtomicReference<>(hold -> {});
    public final AtomicReference<HttpHandler> createOverride = new AtomicReference<>();
    public final AtomicReference<Runnable> beforeCreate = new AtomicReference<>(() -> {});
    public final AtomicReference<CountDownLatch> createBarrier = new AtomicReference<>();
    private final Map<String, Hold> holds = new ConcurrentHashMap<>();

    public ReservationTestSupport(Supplier<String> broker) {
        this.broker = broker;
        try {
            key = TestIntegrationSupport.writePrivateKey(TestIntegrationSupport.KEYS);
            catalog = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            central = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            catalog.setExecutor(executor);
            central.setExecutor(executor);
            catalog.createContext("/", this::catalog);
            central.createContext("/", this::central);
            catalog.start();
            central.start();
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot create fictional providers", failure);
        }
    }

    public Map<String, String> settings() {
        return Map.of(
                "CATALOG_BASE_URL",
                "http://127.0.0.1:" + catalog.getAddress().getPort(),
                "CATEDRA_BASE_URL",
                "http://127.0.0.1:" + central.getAddress().getPort(),
                "CATEDRA_GROUP_ID",
                GROUP,
                "JWT_PRIVATE_KEY_PATH",
                key.toAbsolutePath().toString());
    }

    public void reset() {
        occupancyQueries.set(0);
        occupanciesOverride.set(null);
        creates.set(0);
        confirmations.set(0);
        credentialFailures.set(0);
        holds.clear();
        last.set(null);
        submittedFirstName.set(null);
        submittedPatient.set(null);
        dropCreation.set(false);
        creationStatus.set(201);
        confirmationStatus.set(202);
        beforeConfirm.set(hold -> {});
        createBarrier.set(null);
        createOverride.set(null);
        beforeCreate.set(() -> {});
    }

    private void catalog(HttpExchange exchange) throws IOException {
        try {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            var jwt = SignedJWT.parse(authorization.substring(7));
            if (!jwt.verify(
                            new RSASSAVerifier(
                                    (RSAPublicKey) TestIntegrationSupport.KEYS.getPublic()))
                    || !jwt.getJWTClaimsSet().getAudience().contains("catalog")
                    || !"service".equals(jwt.getJWTClaimsSet().getStringClaim("token_use"))) {
                throw new IllegalArgumentException();
            }
        } catch (Exception failure) {
            credentialFailures.incrementAndGet();
            reply(exchange, 401, "{}");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/api/internal/catedra/token")) {
            reply(exchange, 200, json.writeValueAsString(Map.of("idToken", token)));
            return;
        }
        if (path.startsWith("/api/internal/catalog/professionals/")) {
            long id = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
            reply(
                    exchange,
                    200,
                    """
                    {"professional":{"id":%d,"enabled":true},"weeklySchedules":[
                    {"id":21,"professionalId":%d,"dayOfWeek":"MONDAY","startTime":"09:00","endTime":"11:00","slotDurationMinutes":30,"enabled":true}],
                    "catalog":{"state":"CURRENT","copyAvailable":true,"appliedVersion":1,"observedVersion":1,
                    "lastCheckedAt":"2026-11-02T11:00:00Z","evaluatedAt":"2026-11-02T11:00:00Z"}}
                    """
                            .formatted(id, id));
            return;
        }
        reply(exchange, 404, "{}");
    }

    private void central(HttpExchange exchange) throws IOException {
        if (!("Bearer " + token).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            credentialFailures.incrementAndGet();
            reply(exchange, 401, "{}");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/api/student/integration")) {
            reply(
                    exchange,
                    200,
                    json.writeValueAsString(
                            Map.of(
                                    "groupId",
                                    GROUP,
                                    "provisioningStatus",
                                    "PROVISIONED",
                                    "kafkaBootstrapServers",
                                    broker.get(),
                                    "kafkaConsumerGroupId",
                                    CONSUMER,
                                    "kafkaAppointmentActionsTopic",
                                    TOPIC,
                                    "kafkaAppointmentPhoneTopic",
                                    "fictional-phone")));
            return;
        }
        if (path.equals("/api/appointment-occupancies")) {
            occupancyQueries.incrementAndGet();
            var custom = occupanciesOverride.get();
            if (custom != null) {
                custom.handle(exchange);
                return;
            }
            var query = query(exchange.getRequestURI().getRawQuery());
            long professional = Long.parseLong(query.get("professionalId"));
            var occupied =
                    holds.values().stream()
                            .filter(
                                    hold ->
                                            hold.professional() == professional
                                                    && hold.date().equals(query.get("from")))
                            .map(
                                    hold ->
                                            Map.of(
                                                    "date",
                                                    hold.date(),
                                                    "startTime",
                                                    hold.time(),
                                                    "status",
                                                    "HELD",
                                                    "expiresAt",
                                                    ReservationProcessTests.HOLD
                                                            .expiresAt()
                                                            .toString()))
                            .toList();
            reply(
                    exchange,
                    200,
                    json.writeValueAsString(
                            Map.of(
                                    "professionalId",
                                    professional,
                                    "from",
                                    query.get("from"),
                                    "to",
                                    query.get("to"),
                                    "occupancies",
                                    occupied)));
            return;
        }
        if (path.equals("/api/appointment-holds") && "POST".equals(exchange.getRequestMethod())) {
            create(exchange);
            return;
        }
        if (path.startsWith("/api/appointment-holds/") && path.endsWith("/confirm")) {
            confirm(exchange);
            return;
        }
        reply(exchange, 404, "{}");
    }

    private void create(HttpExchange exchange) throws IOException {
        creates.incrementAndGet();
        beforeCreate.get().run();
        var custom = createOverride.get();
        if (custom != null) {
            custom.handle(exchange);
            return;
        }
        var body = json.readTree(exchange.getRequestBody().readAllBytes());
        String patient = body.path("externalPatientId").asString();
        submittedPatient.set(patient);
        String slot =
                body.path("professionalId").asLong()
                        + ":"
                        + body.path("date").asString()
                        + ":"
                        + body.path("startTime").asString();
        var barrier = createBarrier.get();
        if (barrier != null) {
            barrier.countDown();
            try {
                if (!barrier.await(10, TimeUnit.SECONDS)) {
                    throw new IOException("Fictional barrier timed out");
                }
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IOException(failure);
            }
        }
        int status = creationStatus.get();
        if (status != 201) {
            reply(exchange, status, "{\"code\":\"SLOT_ALREADY_HELD\"}");
            return;
        }
        var hold =
                new Hold(
                        "hold-" + UUID.randomUUID(),
                        "process-" + UUID.randomUUID(),
                        patient,
                        body.path("professionalId").asLong(),
                        body.path("date").asString(),
                        body.path("startTime").asString());
        if (holds.putIfAbsent(slot, hold) != null) {
            reply(exchange, 409, "{\"code\":\"SLOT_ALREADY_HELD\"}");
            return;
        }
        last.set(hold);
        if (dropCreation.get()) {
            exchange.close();
            return;
        }
        reply(
                exchange,
                201,
                json.writeValueAsString(
                        Map.of(
                                "holdId",
                                hold.id(),
                                "reservationProcessId",
                                hold.process(),
                                "status",
                                "HELD",
                                "expiresAt",
                                ReservationProcessTests.HOLD.expiresAt().toString())));
    }

    private void confirm(HttpExchange exchange) throws IOException {
        confirmations.incrementAndGet();
        String path = exchange.getRequestURI().getPath();
        String id =
                path.substring(
                        "/api/appointment-holds/".length(), path.length() - "/confirm".length());
        var hold =
                holds.values().stream()
                        .filter(value -> value.id().equals(id))
                        .findFirst()
                        .orElseThrow();
        var body = json.readTree(exchange.getRequestBody().readAllBytes());
        submittedFirstName.set(body.path("patientFirstName").asString());
        if (!hold.patient().equals(body.path("externalPatientId").asString())) {
            reply(exchange, 400, "{\"code\":\"EXTERNAL_PATIENT_MISMATCH\"}");
            return;
        }
        beforeConfirm.get().accept(hold);
        int status = confirmationStatus.get();
        if (status != 202) {
            reply(exchange, status, "{\"code\":\"HOLD_NOT_FOUND\"}");
            return;
        }
        reply(
                exchange,
                202,
                json.writeValueAsString(
                        Map.of(
                                "reservationProcessId",
                                hold.process(),
                                "status",
                                "WAITING_FOR_PHONE",
                                "expiresAt",
                                ReservationProcessTests.HOLD.expiresAt().toString())));
    }

    /** Simula la liberación central de posiciones, sin inferir propiedad en el backend. */
    public void releaseHolds() {
        holds.clear();
    }

    public static String event(Hold hold, String type, String eventId) {
        return """
        {"eventId":"%s","eventType":"%s","schemaVersion":1,"reservationProcessId":"%s","holdId":"%s",
         "groupId":"reservation-test","requiredField":"PHONE_NUMBER","expiresAt":"2026-11-02T11:05:00Z",
         "occurredAt":"2026-11-02T11:00:00Z","requestEventId":"request-1","reason":"HOLD_EXPIRED","futureField":true}
        """
                .formatted(eventId, type, hold.process(), hold.id());
    }

    private static Map<String, String> query(String query) {
        var result = new HashMap<String, String>();
        for (String pair : query.split("&")) {
            var parts = pair.split("=", 2);
            result.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return result;
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        TestIntegrationSupport.respond(exchange, status, body);
    }

    @Override
    public void close() {
        catalog.stop(0);
        central.stop(0);
        executor.shutdownNow();
        try {
            Files.deleteIfExists(key);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    public record Hold(
            String id,
            String process,
            String patient,
            long professional,
            String date,
            String time) {}
}
