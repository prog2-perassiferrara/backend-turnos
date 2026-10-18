package com.prog2.appointment.integration;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Dos proveedores HTTP ficticios aislados; claves efímeras, sin red de cátedra ni archivos privados del usuario. */
public final class TestIntegrationSupport implements AutoCloseable {
    public static final String GROUP = "appointment-test";
    public static final KeyPair KEYS = generateKeys(2048);
    private final HttpServer catalog;
    private final HttpServer central;
    private final Path privatePath;
    public final AtomicInteger catalogCalls = new AtomicInteger();
    public final AtomicInteger centralCalls = new AtomicInteger();
    public final AtomicInteger unexpectedCalls = new AtomicInteger();
    public final AtomicInteger catalogStatus = new AtomicInteger(200);
    public final AtomicInteger centralStatus = new AtomicInteger(200);
    public final AtomicReference<String> catalogBody = new AtomicReference<>();
    public final AtomicReference<String> centralBody = new AtomicReference<>(configuration("PROVISIONED"));
    public final AtomicReference<String> catalogBearer = new AtomicReference<>();
    public final AtomicReference<String> centralBearer = new AtomicReference<>();
    public final AtomicReference<String> serviceValidationFailure = new AtomicReference<>();
    public final String centralToken;

    public TestIntegrationSupport() {
        try {
            privatePath = writePrivateKey(KEYS);
            centralToken = token(Instant.now().plusSeconds(3600));
            catalogBody.set("{\"idToken\":\"" + centralToken + "\",\"futureField\":true}");
            catalog = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            central = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            catalog.createContext("/", this::serveCatalog);
            central.createContext("/", this::serveCentral);
            catalog.start();
            central.start();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot start fictional providers", exception);
        }
    }

    /** @return parámetros ficticios que permiten probar el contexto real sin .env */
    public Map<String, String> settings() {
        return Map.of("CATALOG_BASE_URL", catalogUrl().toString(), "CATEDRA_BASE_URL", centralUrl().toString(),
                "CATEDRA_GROUP_ID", GROUP, "JWT_PRIVATE_KEY_PATH", privatePath.toAbsolutePath().toString());
    }

    public URI catalogUrl() {
        return URI.create("http://127.0.0.1:" + catalog.getAddress().getPort());
    }

    public URI centralUrl() {
        return URI.create("http://127.0.0.1:" + central.getAddress().getPort());
    }

    public RSAPrivateCrtKey privateKey() {
        return (RSAPrivateCrtKey) KEYS.getPrivate();
    }

    public Path privatePath() {
        return privatePath;
    }

    private void serveCatalog(HttpExchange exchange) throws IOException {
        if (!"/api/internal/catedra/token".equals(exchange.getRequestURI().getPath())
                || !"GET".equals(exchange.getRequestMethod())) {
            unexpectedCalls.incrementAndGet();
            respond(exchange, 404, "{}");
            return;
        }
        catalogCalls.incrementAndGet();
        catalogBearer.set(exchange.getRequestHeaders().getFirst("Authorization"));
        validateServiceBearer(catalogBearer.get());
        respond(exchange, catalogStatus.get(), catalogBody.get());
    }

    private void serveCentral(HttpExchange exchange) throws IOException {
        if (!"/api/student/integration".equals(exchange.getRequestURI().getPath())
                || !"GET".equals(exchange.getRequestMethod())) {
            unexpectedCalls.incrementAndGet();
            respond(exchange, 404, "{}");
            return;
        }
        centralCalls.incrementAndGet();
        centralBearer.set(exchange.getRequestHeaders().getFirst("Authorization"));
        respond(exchange, centralStatus.get(), centralBody.get());
    }

    private void validateServiceBearer(String authorization) {
        try {
            SignedJWT jwt = SignedJWT.parse(authorization.substring("Bearer ".length()));
            var claims = jwt.getJWTClaimsSet();
            if (!jwt.verify(new RSASSAVerifier((RSAPublicKey) KEYS.getPublic()))
                    || !JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())
                    || !"backend-turnos".equals(claims.getIssuer())
                    || !"backend-turnos".equals(claims.getSubject())
                    || !claims.getAudience().contains("catalog")
                    || !"service".equals(claims.getStringClaim("token_use"))
                    || !"ROLE_CATALOG_INTEGRATION".equals(claims.getStringClaim("auth"))
                    || !claims.getExpirationTime().toInstant().equals(claims.getIssueTime().toInstant().plusSeconds(300))) {
                serviceValidationFailure.set("Service JWT did not satisfy the fictional provider contract");
            }
        } catch (java.text.ParseException | JOSEException | RuntimeException _) {
            serviceValidationFailure.set("Invalid service credential");
        }
    }

    public static String configuration(String status) {
        return """
                {"groupId":"appointment-test","provisioningStatus":"%s",
                 "kafkaBootstrapServers":"fictional-broker:9092","kafkaConsumerGroupId":"assigned-consumer",
                 "kafkaAppointmentActionsTopic":"assigned-actions","kafkaAppointmentPhoneTopic":"assigned-phone",
                 "futureField":true}
                """.formatted(status);
    }

    public static String token(Instant expiry) {
        try {
            var builder = new JWTClaimsSet.Builder().issuer("fictional-central").subject("fictional-account");
            if (expiry != null) {
                // Nimbus exige Date en este método; el resto del cálculo temporal utiliza java.time.
                builder.expirationTime(Date.from(expiry));
            }
            var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), builder.build());
            jwt.sign(new RSASSASigner(KEYS.getPrivate()));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Cannot sign fictional central token", exception);
        }
    }

    public static KeyPair generateKeys(int bits) {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(bits);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot generate fictional keys", exception);
        }
    }

    public static Path writePrivateKey(KeyPair keys) throws IOException {
        Path directory = Path.of("target", "test-keys");
        Files.createDirectories(directory);
        Path file = Files.createTempFile(directory, "fictional-turnos-", ".pem");
        file.toFile().deleteOnExit();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(keys.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        Files.writeString(file, pem);
        return file;
    }

    public static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        } finally {
            exchange.close();
        }
    }

    @Override
    public void close() {
        catalog.stop(0);
        central.stop(0);
        try {
            Files.deleteIfExists(privatePath);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot remove fictional key", exception);
        }
    }
}
