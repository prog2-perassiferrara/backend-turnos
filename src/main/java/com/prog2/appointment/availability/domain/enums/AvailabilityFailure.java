package com.prog2.appointment.availability.domain.enums;

/** Motivos propios de disponibilidad; no contienen cuerpos remotos ni credenciales. */
public enum AvailabilityFailure {
    INVALID_AVAILABILITY_QUERY,
    PROFESSIONAL_NOT_FOUND,
    PROFESSIONAL_DISABLED,
    CATALOG_NOT_CURRENT,
    CATALOG_UNAVAILABLE,
    OCCUPANCIES_UNAVAILABLE,
    TECHNICAL_INTEGRATION_UNAVAILABLE,
    CENTRAL_TOKEN_REJECTED
}
