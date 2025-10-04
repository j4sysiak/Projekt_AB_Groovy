package com.example.sat.dsl2

import groovy.transform.Canonical

@Canonical
class ServerConfig {
    String host
    String defaultRespondMediaType
    def request
    ResponseConfig response

    void response(Closure c) {
        def resp = new ResponseConfig()
        c.delegate = resp
        c.resolveStrategy = Closure.DELEGATE_FIRST
        c()
        this.response = resp
    }
}

@Canonical
class ResponseConfig {
    int status
    String mediaType
    String body
}
