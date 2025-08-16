// src/test/groovy/com/example/sat/SubscriptionNotificationSpec.groovy
package com.example.sat

import com.example.sat.dto.PhotoCollectionManifest
import com.example.sat.dto.PhotoTask
import com.example.sat.dto.PhotoTaskStatus
import com.example.sat.dto.SubscriptionRequest
import com.example.sat.http.SimpleHttpClient
import com.example.sat.server.GroundControlServerApp
import groovy.json.JsonSlurper
import okhttp3.mockwebserver.MockResponse      // <-- NOWY IMPORT
import okhttp3.mockwebserver.MockWebServer      // <-- NOWY IMPORT
import spock.lang.Shared
import spock.lang.Specification
import java.util.concurrent.TimeUnit           // <-- NOWY IMPORT

class SubscriptionNotificationSpec extends Specification {

    @Shared GroundControlServerApp grundServer
    @Shared SimpleHttpClient grundClient

    // ZMIANA: Zamiast drugiej instancji naszej aplikacji, używamy dedykowanego MockWebServer
    @Shared MockWebServer webhookReceiverServer


    def setupSpec() {
        // 1. Uruchom serwer GRUND
        grundServer = new GroundControlServerApp()
        grundServer.start(0, "") // Nie potrzebuje satelity
        grundClient = new SimpleHttpClient("http://localhost:${grundServer.getPort()}")

        // 2. Stwórz i uruchom MockWebServer
        webhookReceiverServer = new MockWebServer()
        webhookReceiverServer.start() // Uruchomi się na losowym, wolnym porcie
    }

    def cleanupSpec() {
        grundServer?.stop()
        // ZMIANA: Zamykamy MockWebServer za pomocą jego metody shutdown()
        webhookReceiverServer?.shutdown()
    }

    def cleanup() {
        grundServer.resetState()
        // Nie musimy już czyścić listy, bo MockWebServer sam zarządza stanem
    }

    def "should receive a notification when a subscribed mission is completed"() {
        given: "a mission is planned and a subscription is created"
        // "Programujemy" odpowiedź naszego fałszywego serwera.
        // Mówimy mu: "Na następne żądanie, które otrzymasz, odpowiedz statusem 200 OK".
        webhookReceiverServer.enqueue(new MockResponse().setResponseCode(200).setBody("Notification received"))

        // Przygotowanie danych w grundServer - bez zmian
        def testTask = new PhotoTask(taskId: 'T1', status: PhotoTaskStatus.PENDING)
        def missionManifest = new PhotoCollectionManifest(
                missionId: 'M-123',
                tasks: [testTask],
                status: "UPLOADED"
        )
        grundServer.missionManifests['M-123'] = missionManifest

        // Tworzymy subskrypcję, podając adres URL naszego MockWebServer
        def subRequest = new SubscriptionRequest(
                missionId: 'M-123',
                eventType: 'MISSION_COMPLETED',
                // ZMIANA: Używamy metody .url(), aby uzyskać pełny adres MockWebServer
                callbackUrl: webhookReceiverServer.url("/webhook-endpoint").toString()
        )
        grundClient.sendGenericPost("/subscriptions", subRequest)

        when: "the final data for the mission arrives at ground control"
        // Dane testowe - bez zmian, ale upewnijmy się, że są kompletne
        def finalPhotoData = [[
                                      missionId: 'M-123',
                                      photoId: 'T1',
                                      content: 'Test content',
                                      timestamp: System.currentTimeMillis(),
                                      quality: 99
                              ]]
        grundClient.sendGenericPost("/data/ingest", finalPhotoData)

        then: "the webhook receiver gets a correct POST request"
        // ZMIANA: Zamiast Awaitility, używamy wbudowanej metody MockWebServer,
        // która czeka na żądanie (z timeoutem dla bezpieczeństwa).
        def receivedRequest = webhookReceiverServer.takeRequest(5, TimeUnit.SECONDS)

        and: "the received request has correct properties"
        receivedRequest != null // Upewniamy się, że żądanie w ogóle dotarło
        receivedRequest.method == "POST"
        receivedRequest.path == "/webhook-endpoint"

        def notification = new JsonSlurper().parseText(receivedRequest.getBody().readUtf8())
        notification.eventType == "MISSION_COMPLETED"
        notification.data.missionId == "M-123"
        notification.data.photoCount == 1
    }
}