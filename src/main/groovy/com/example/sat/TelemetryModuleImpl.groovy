package com.example.sat

import groovy.transform.Canonical

@Canonical
class TelemetryModuleImpl implements TelemetryModule {
    String telemetryEndpoint // np. "udp://telemetry.alpha.com:5555"

    @Override
    void sendEvent(String eventType, Map<String, Object> payload) {
        println "TELEMETRY to [${telemetryEndpoint}]: Event '${eventType}' with payload ${payload}"
    }
}