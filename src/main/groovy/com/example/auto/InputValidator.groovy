package com.example.auto

import com.example.auto.exceptions.ValidationException

// Prosty interfejs dla naszego walidatora
interface InputValidator {
    void validateAutoId(String autoId) throws ValidationException
}