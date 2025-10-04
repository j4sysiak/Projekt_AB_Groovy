package com.example.sat.dsl2

import com.example.sat.dsl2.RestDSL
import spock.lang.Specification

@RestDSL
class MyRestDslSpec extends Specification {

    def "should execute a GET request using a nested DSL"() {
        when: "a request is defined with request and response blocks"
        Map res = get("/health") { cfg ->
            println "CFG: $cfg"
            assert cfg != null : 'cfg przekazany do closure jest null!'
            cfg.response {
                status 200
                mediaType "application/json"
                body '{"status":"OK_FROM_NESTED_DSL"}'
            }
        }

        then: "the response is successful"
        res.statusCode == 200
        res.json.status == "OK_FROM_NESTED_DSL"
    }
}
