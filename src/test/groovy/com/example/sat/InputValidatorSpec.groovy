package com.example.sat

import com.example.sat.exceptions.ValidationException
import spock.lang.Specification
import spock.lang.Unroll

class InputValidatorSpec extends Specification {

    // Nasz obiekt testowany
    def validator = new InputValidatorImpl()

    def "should pass validation for a correct satellite ID"() {
        when: "a valid ID is validated"
        validator.validateSatelliteId("SAT-12345")

        then: "no exception is thrown"
        noExceptionThrown()
    }

    // Używamy @Unroll, aby Spock wygenerował osobne raporty dla każdego przypadku testowego
    @Unroll
    def "should throw ValidationException for invalid ID: '#invalidId'"() {
        when: "an invalid ID is validated"
        validator.validateSatelliteId(invalidId)

        then: "a ValidationException is thrown"
        thrown(ValidationException)

        // Tabela danych wejściowych dla tego testu
        where:
        invalidId | _
        null      | _
        "INVALID" | _
        "SAt-"    | _
        "SAT-1"   | _ // Zbyt krótkie
    }
}