package com.example.sat.fakes
import com.example.sat.TelemetryModule

class ManualMockTelemetryModule implements TelemetryModule {
    @Override void sendEvent(String eventType, Map<String, Object> payload) {}
}