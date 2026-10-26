package com.example.auto.fakes
import com.example.auto.IAlertingService

class ManualMockAlertingService implements IAlertingService {
    List<AlertRecord> alerts = []

    @Override
    void raiseAlert(String alertType, String message) {
        alerts.add(new AlertRecord(type: alertType, message: message))
    }
}