package com.example.sat

interface TelemetryModule {
    void sendEvent(String eventType, Map<String, Object> payload)
}