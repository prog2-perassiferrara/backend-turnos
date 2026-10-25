package com.prog2.appointment.user.infrastructure.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Cuenta caracteres completos; NotNull rechaza ausencia y nunca se copia el valor al diagnóstico. */
public final class PasswordLengthValidator implements ConstraintValidator<PasswordLength, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        int length = value.codePointCount(0, value.length());
        return length >= 6 && length <= 100;
    }
}
