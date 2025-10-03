package com.example.sat.dsl


import spock.lang.Specification

@RestDSL
class MyRestDslSpec extends Specification {

    def "should execute a GET request using the injected DSL method"() {
        when: "a GET request is defined and executed in a single block"

        Map res = get("/health") {
            host = "http://example.com"
            defaultRespondMediaType = "application/json"
        }

        then: "the response is successful"
        res.statusCode == 200
        res.json.status == "OK_FROM_DSL"
    }
}