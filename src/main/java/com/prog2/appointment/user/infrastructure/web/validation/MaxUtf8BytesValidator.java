package com.prog2.appointment.user.infrastructure.web.validation;

import java.nio.charset.StandardCharsets;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Comprueba la entrada completa; los nulos se delegan a NotNull sin imprimir el valor rechazado. */
public final class MaxUtf8BytesValidator implements ConstraintValidator<MaxUtf8Bytes, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
