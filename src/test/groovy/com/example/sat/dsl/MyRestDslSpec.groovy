/*
package com.example.sat.dsl

import com.example.sat.test.dsl.RestDSL
import spock.lang.Specification

// Nasza customowa adnotacja w akcji!
@RestDSL
class MyRestDslSpec extends Specification {

    // Definicja serwera jako closure.
    // Metody 'get' i 'post' będą miały do niej dostęp.
    */
/*
    def server = {
        host = "http://example.com"
        defaultRespondMediaType = "application/json"
    }*//*


    def "should execute a GET request using the injected DSL method"() {
        when: "a GET request is defined and executed in a single block"
        // ZMIANA: Przekazujemy closure bezpośrednio jako ostatni argument.
        // Groovy pozwala na taką piękną składnię.
        Map res = get("/health") {
            // Ten blok kodu to nasze closure
            host = "http://example.com"
            defaultRespondMediaType = "application/json"
        }

        then: "the response is successful"
        res.statusCode == 200
        res.json.status == "OK_FROM_DSL"
    }
}*/
