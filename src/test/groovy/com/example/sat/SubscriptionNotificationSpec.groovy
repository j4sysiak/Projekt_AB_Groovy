// src/test/groovy/com/example/sat/SubscriptionNotificationSpec.groovy
package com.example.sat

import com.example.sat.dto.SubscriptionRequest
import com.example.sat.http.SimpleHttpClient
import com.example.sat.server.GroundControlServerApp
import groovy.json.JsonSlurper
import spock.lang.Shared
import spock.lang.Specification
import static java.util.concurrent.TimeUnit.SECONDS
import static org.awaitility.Awaitility.await

class SubscriptionNotificationSpec extends Specification {

    @Shared GroundControlServerApp grundServer
    @Shared SimpleHttpClient grundClient

    // Nasz fałszywy serwer, który będzie udawał system zewnętrzny
    @Shared GroundControlServerApp webhookReceiverServer
    // Lista powiadomień, które otrzymał nasz fałszywy serwer
    @Shared List<Map> receivedNotifications = []

    def setupSpec() {
        // 1. Uruchom serwer GRUND
        grundServer = new GroundControlServerApp()
        grundServer.start(0, "") // Nie potrzebuje satelity
        grundClient = new SimpleHttpClient("http://localhost:${grundServer.getPort()}")

        // 2. Uruchom serwer, który będzie odbiorcą webhooka
        webhookReceiverServer = new GroundControlServerApp()

        // === POPRAWIONA KOLEJNOŚĆ ===
        // NAJPIERW URUCHOM SERWER, żeby pole 'http' zostało zainicjalizowane
        webhookReceiverServer.start(0, "")

        // TERAZ, GDY SERWER DZIAŁA, A 'http' NIE JEST JUŻ NULL, MOŻESZ DODAWAĆ ENDPOINTY
        webhookReceiverServer.http.post("/webhook-endpoint") { req, res ->
            println "WEBHOOK_RECEIVER: Received a notification!"
            def payload = new JsonSlurper().parseText(req.body())
            receivedNotifications.add(payload)
            res.status(200)
            return "OK"
        }
    }

    def cleanupSpec() {
        grundServer?.stop()
        webhookReceiverServer?.stop()
    }

    def cleanup() {
        grundServer.resetState()
        receivedNotifications.clear()
    }

    def "should receive a notification when a subscribed mission is completed"() {
        given: "a mission is planned and a subscription is created"
        // Symulujemy, że misja M-123 oczekuje na 1 zdjęcie
        def missionManifest = [tasks: [[taskId: 'T1']]]
        grundServer.missionManifests['M-123'] = missionManifest

        // Tworzymy subskrypcję na tę misję
        def subRequest = new SubscriptionRequest (
                missionId: 'M-123',
                eventType: 'MISSION_COMPLETED',
                callbackUrl: "http://localhost:${webhookReceiverServer.getPort()}/webhook-endpoint"
        )
        grundClient.sendGenericPost("/subscriptions", subRequest)

        when: "the final data for the mission arrives at ground control"
        def finalPhotoData = [
                [
                        missionId: 'M-123',
                        photoId: 'P1',
                        content: 'Test photo content',
                        timestamp: System.currentTimeMillis(), // Musimy dostarczyć timestamp
                        quality: 98                          // Musimy dostarczyć quality
                ]
        ]
        grundClient.sendGenericPost("/data/ingest", finalPhotoData)

        then: "the webhook receiver gets a notification"
        // Czekamy maksymalnie 5 sekund, bo powiadomienie jest wysyłane asynchronicznie
        await().atMost(5, SECONDS).until { receivedNotifications.size() == 1 }

        and: "the notification payload is correct"
        def notification = receivedNotifications.first()
        notification.eventType == "MISSION_COMPLETED"
        notification.data.missionId == "M-123"
        notification.data.photoCount == 1
    }
}