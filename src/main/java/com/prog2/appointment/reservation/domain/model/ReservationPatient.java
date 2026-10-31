package com.prog2.appointment.reservation.domain.model;

/** Perfil capturado de la cuenta: nunca procede del cuerpo enviado por KMP. */
public record ReservationPatient(long ownerId, String firstName, String lastName) {
    public ReservationPatient {
        if (ownerId <= 0 || !valid(firstName) || !valid(lastName)) {
            throw new IllegalArgumentException("Profile cannot be used for a reservation");
        }
    }

    private static boolean valid(String value) {
        return value != null && !value.isBlank() && value.length() >= 2 && value.length() <= 100;
    }

    /**
     * @return identificador estable dentro de la integración, independiente del login
     */
    public String externalPatientId() {
        return "user-" + ownerId;
    }

    @Override
    public String toString() {
        return "ReservationPatient[REDACTED]";
    }
}
