package com.prog2.appointment.integration.infrastructure.persistence.adapter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import com.prog2.appointment.integration.domain.model.TechnicalAccess;
import com.prog2.appointment.integration.domain.ports.out.TechnicalAccessRepository;

/** Repository en memoria: sincroniza sesión, rechazos y cierre, sin sostener locks durante HTTP. */
public final class InMemoryTechnicalAccessAdapter implements TechnicalAccessRepository {
    private final Clock clock;
    private final Set<String> rejectedFingerprints = new HashSet<>();
    private TechnicalAccess current;
    private boolean closed;

    public InMemoryTechnicalAccessAdapter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public synchronized Optional<TechnicalAccess> current() {
        return Optional.ofNullable(current);
    }

    @Override
    public synchronized boolean publishIfAccepted(TechnicalAccess access) {
        if (closed || !access.token().isUsableAt(clock.instant()) || isRejected(access.token().value())) {
            return false;
        }
        current = access;
        return true;
    }

    @Override
    public synchronized void rejectToken(String token) {
        // Guardar huellas impide rehabilitar tokens rechazados sin conservar otra copia del secreto.
        rejectedFingerprints.add(fingerprint(token));
        if (current != null && current.token().value().equals(token)) {
            current = null;
        }
    }

    @Override
    public synchronized boolean isRejected(String token) {
        return rejectedFingerprints.contains(fingerprint(token));
    }

    @Override
    public synchronized void close() {
        closed = true;
        current = null;
        rejectedFingerprints.clear();
    }

    private static String fingerprint(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
