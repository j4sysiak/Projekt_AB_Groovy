package com.example.auto.services

import com.example.auto.db.ISubscriptionRepository // <-- Zależność od
import com.example.auto.dto.NotificationPayload
import com.example.auto.http.INotificationClient   // <-- Zależność od interfejsu

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
    void publishEvent(String eventType, String actionId, Map<String, Object> eventData) {
        println "NOTIFICATION_SERVICE: Received event '${eventType}' for action '${actionId}'."

           def subscriptions = repository.findSubscriptionsByEvent(eventType, actionId)

        if (subscriptions.isEmpty()) {
            println "NOTIFICATION_SERVICE: No subscribers found for this event."
            return
        }
        println "NOTIFICATION_SERVICE: Found ${subscriptions.size()} subscriber(s). Preparing notifications..."
        subscriptions.each { sub ->
            def callbackUrl = sub.callbackUrl
            def payload = new NotificationPayload (
                    eventType: eventType,
                    data: [actionId: actionId] + eventData
            )
            println "NOTIFICATION_SERVICE: Sending notification to ${callbackUrl}..."
            // Używamy naszego klienta HTTP do wysłania webhooka
            client.send(callbackUrl, payload)  //callbackUrl to oczywiście http://.../webhook.
        }
    }
}