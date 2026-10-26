package com.example.auto

import com.example.auto.exceptions.ValidationException

class InputValidatorImpl implements InputValidator {
    private static final String ID_PREFIX = "AUTO-"
    private static final int MIN_LENGTH = ID_PREFIX.length() + 2

    @Override
    void validateAutoId(String autoId) throws ValidationException {
        println "VALIDATOR: Validating ID: ${autoId}"
        if (autoId == null) {
            throw new ValidationException("Auto ID cannot be null.")
        }
        if (!autoId.startsWith(ID_PREFIX)) {
            throw new ValidationException("Invalid format: Auto ID must start with '${ID_PREFIX}'.")
        }
        if (autoId.length() < MIN_LENGTH) {
            throw new ValidationException("Invalid format: Auto ID is too short.")
        }
        println "VALIDATOR: ID '${autoId}' is valid."
    }
}