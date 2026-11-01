package com.prog2.appointment.reservation.domain.model;

/** Nombre histórico verificado; no representa un profesional vigente de Catálogo. */
public record ProfessionalName(String firstName, String lastName) {
    public ProfessionalName {
        if (!usable(firstName) || !usable(lastName)) {
            throw new IllegalArgumentException("Invalid professional name");
        }
    }

    /**
     * @return nombre completo utilizable o null cuando la descripción no está disponible
     */
    public static ProfessionalName from(String firstName, String lastName) {
        return usable(firstName) && usable(lastName)
                ? new ProfessionalName(firstName, lastName)
                : null;
    }

    private static boolean usable(String value) {
        return value != null && !value.isBlank() && value.length() <= 255;
    }

    @Override
    public String toString() {
        return "ProfessionalName[present]";
    }
}
