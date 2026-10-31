package com.prog2.appointment.user.domain.model;

/** Perfil mínimo para consumidores internos; nunca contiene contraseña ni autoridades. */
public record UserProfile(long id, String firstName, String lastName, boolean activated) { }
