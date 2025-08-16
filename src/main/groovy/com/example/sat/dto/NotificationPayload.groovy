package com.example.sat.dto

import java.text.SimpleDateFormat
import groovy.transform.Canonical

//  To jest to, co my będziemy wysyłać do klienta jako powiadomienie.
@Canonical
class NotificationPayload {
    String eventId = "EVT_" + System.nanoTime()
    String eventType
    // Używamy standardowego SimpleDateFormat z Javy
    String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(new Date())
    Map<String, Object> data // Elastyczne pole na szczegóły zdarzenia
}
