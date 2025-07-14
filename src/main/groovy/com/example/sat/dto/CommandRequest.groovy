package com.example.sat.dto
import groovy.transform.Canonical

@Canonical
class CommandRequest {
    String command
    String authToken
    long timestamp = System.currentTimeMillis()
}