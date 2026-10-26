package com.example.auto

import com.example.auto.exceptions.ValidationException
import spock.lang.Specification
import spock.lang.Unroll

class InputValidatorSpec extends Specification {

    // Nasz obiekt testowany
    def validator = new InputValidatorImpl()

    @Unroll
    def "should pass validation for a correct auto ID: '#validId'"() {
        when: "a valid ID is validated"
        validator.validateAutoId(validId)

        then: "no exception is thrown"
        noExceptionThrown()

        where:
        validId << ["AUTO-12", "AUTO-12345"]
    }

    // Używamy @Unroll, aby Spock wygenerował osobne raporty dla każdego przypadku testowego
    @Unroll
    def "should throw ValidationException for invalid ID: '#invalidId'"() {
        when: "an invalid ID is validated"
        validator.validateAutoId(invalidId)

        then: "a ValidationException is thrown"
        thrown(ValidationException)

        // Tabela danych wejściowych dla tego testu
        where:
        invalidId | _
        null      | _
        "INVALID" | _
        "AUT-"    | _
        "AUTO-"   | _
        "AUTO-1"  | _ // Zbyt krótkie
    }
}