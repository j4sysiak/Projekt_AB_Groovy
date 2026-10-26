package com.example.auto.dto
import groovy.transform.Canonical

@Canonical
class CommandResponse {
    String status
    String message
    double batteryLevel
}