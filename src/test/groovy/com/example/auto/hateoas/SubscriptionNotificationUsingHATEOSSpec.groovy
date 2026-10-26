package com.example.auto.hateoas

import com.example.auto.dto.PhotoCollectionAction
import com.example.auto.dto.PhotoTask
import com.example.auto.dto.PhotoTaskStatus
import com.example.auto.dto.SubscriptionRequest
import com.example.auto.fakes.FakeRelationalDB
import com.example.auto.fakes.HttpServerFactory
import com.example.auto.http.SimpleHttpClient
import com.example.auto.server.AutoControlServerApp
import groovy.json.JsonSlurper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import spock.lang.Shared
import spock.lang.Specification
import java.util.concurrent.TimeUnit

class SubscriptionNotificationUsingHATEOSSpec extends Specification {

    @Shared AutoControlServerApp platzServer
    @Shared SimpleHttpClient platzClient
    @Shared MockWebServer webhookReceiverServer
    @Shared Map<String, PhotoCollectionAction> actionManifests

    def setupSpec() {
        // === ZMIANA: Pełne wstrzykiwanie zależności ===

        // 1. Stwórz testowe implementacje zależności
        def fakeDb = new FakeRelationalDB()
        def fakeHttpServer = HttpServerFactory.create()
        actionManifests = [:] // Inicjalizujemy mapę, którą będziemy współdzielić

        // 2. Stwórz i uruchom AutoControlServerApp, używając konstruktora testowego
        platzServer = new AutoControlServerApp(fakeDb, fakeHttpServer, actionManifests)
        platzServer.start(0, "")
        def platzServerUrl = "http://localhost:${platzServer.getPort()}"
        platzClient = new SimpleHttpClient(platzServerUrl)

        // 3. Stwórz i uruchom MockWebServer
        webhookReceiverServer = new MockWebServer()
        webhookReceiverServer.start()
    }

    def cleanupSpec() {
        platzServer?.stop()
        webhookReceiverServer?.shutdown()
    }

    def cleanup() {
        platzServer.resetState()
    }

    def "should discover subscription endpoint via HATEOAS and receive a notification"() {
        given: "a action is planned and the client is ready to subscribe"
        webhookReceiverServer.enqueue(new MockResponse().setResponseCode(200).setBody("Notification received"))

        def testTask = new PhotoTask(taskId: 'T1', status: PhotoTaskStatus.PENDING)
        def actionManifest = new PhotoCollectionAction(
                actionId: 'M-123',
                tasks: [testTask],
                status: "UPLOADED"
        )
        // ZMIANA: Używamy teraz pola z testu do wstawienia manifestu
        actionManifests['M-123'] = actionManifest

        // === Logika HATEOAS ===
        def rootResponse = platzClient.getData("/")
        def subscriptionUrl = rootResponse.json._links.'create-subscription'.href

        def subRequest = new SubscriptionRequest(
                actionId: 'M-123',
                eventType: 'ACTION_COMPLETED',
                callbackUrl: webhookReceiverServer.url("/webhook-endpoint").toString()
        )
        def subResponse = platzClient.sendGenericPost(subscriptionUrl, subRequest)
        subResponse.statusCode == 201

        when: "the final data for the action arrives at ground control"
        def finalPhotoData = [[
                                      actionId: 'M-123',
                                      photoId: 'T1',
                                      content: 'Test content',
                                      timestamp: System.currentTimeMillis(),
                                      quality: 99
                              ]]
        platzClient.sendGenericPost("/data/ingest", finalPhotoData)

        then: "the webhook receiver gets a correct POST request"
        def receivedRequest = webhookReceiverServer.takeRequest(5, TimeUnit.SECONDS)

        and: "the received request has correct properties"
        receivedRequest != null
        receivedRequest.method == "POST"
        receivedRequest.path == "/webhook-endpoint"

        def notification = new JsonSlurper().parseText(receivedRequest.getBody().readUtf8())
        notification.eventType == "ACTION_COMPLETED"
        notification.data.actionId == "M-123"
        notification.data.photoCount == 1
    }
}