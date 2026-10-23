package com.prog2.appointment.user.domain.model;

import java.util.Locale;

/** Perfil de alta previamente validado; no contiene contraseña ni campos internos asignables. */
public record RegistrationData(String login, String firstName, String lastName, String email,
        String imageUrl, String langKey) {

    /** Normaliza identificadores y valores opcionales sin alterar nombres ni contraseñas. */
    public RegistrationData {
        login = normalizeIdentifier(login);
        email = normalizeIdentifier(email);
        imageUrl = imageUrl == null || imageUrl.isBlank() ? null : imageUrl;
        langKey = langKey == null ? "es" : langKey;
    }

    /**
     * @param value login o email, o null antes de validar una entrada HTTP
     * @return identificador en minúsculas con reglas independientes del idioma del servidor
     */
    public static String normalizeIdentifier(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
