package com.example.sat

class AlertingServiceImpl implements IAlertingService {
    private final String alertEndpoint

    AlertingServiceImpl(String alertEndpoint) {
        this.alertEndpoint = alertEndpoint
    }

    @Override
    void raiseAlert(String alertType, String message) {
        // W prawdziwym świecie to wysyłałoby SMS, e-mail lub komunikat do innego systemu
        println "ALERT to [${alertEndpoint}]: [${alertType}] - ${message}"
    }
}