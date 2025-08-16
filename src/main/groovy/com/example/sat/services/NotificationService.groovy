// src/main/groovy/com/example/sat/services/NotificationService.groovy
package com.example.sat.services

import com.example.sat.db.ISubscriptionRepository // <-- Zależność od interfejsu
import com.example.sat.dto.NotificationPayload
import com.example.sat.http.INotificationClient   // <-- Zależność od interfejsu

class NotificationService {

    private final ISubscriptionRepository repository
    private final INotificationClient client

    // Wstrzykujemy zależności przez konstruktor (Dependency Injection)
    NotificationService(ISubscriptionRepository repository, INotificationClient client) {
        this.repository = repository
        this.client = client
    }

    /**
     * Główna metoda publiczna. Znajduje subskrybentów i wysyła im powiadomienia.
     */
    void publishEvent(String eventType, String missionId, Map<String, Object> eventData) {
        println "NOTIFICATION_SERVICE: Received event '${eventType}' for mission '${missionId}'."
        def subscriptions = repository.findByEventTypeAndMissionId(eventType, missionId)
        if (subscriptions.isEmpty()) {
            println "NOTIFICATION_SERVICE: No subscribers found for this event."
            return
        }
        println "NOTIFICATION_SERVICE: Found ${subscriptions.size()} subscriber(s). Preparing notifications..."
        subscriptions.each { sub ->
            def callbackUrl = sub.callbackUrl
            def payload = new NotificationPayload(
                    eventType: eventType,
                    data: [missionId: missionId] + eventData // Łączymy dane
            )
            println "NOTIFICATION_SERVICE: Sending notification to ${callbackUrl}..."
            // Używamy naszego klienta HTTP do wysłania webhooka
            client.send(callbackUrl, payload)
        }
    }
}