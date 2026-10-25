package com.prog2.appointment.user.infrastructure.web.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** Longitud de contraseña compartida por registro y login, medida en puntos de código Unicode. */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordLengthValidator.class)
public @interface PasswordLength {
    String message() default "must contain between 6 and 100 Unicode characters";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
