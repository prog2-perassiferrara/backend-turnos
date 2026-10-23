package com.prog2.appointment.user.infrastructure.web.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** Restricción de contraseña compartida: el máximo de BCrypt se mide en bytes, no en caracteres. */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
public @interface MaxUtf8Bytes {
    String message() default "must contain at most 72 UTF-8 bytes";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
