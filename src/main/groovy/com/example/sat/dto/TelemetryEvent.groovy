package com.example.sat.dto
import groovy.transform.Canonical

@Canonical
class TelemetryEvent {
    String eventType
    String satelliteId
    Map<String, Object> payload
    long timestamp = System.currentTimeMillis()
}