package com.example.auto

import com.example.auto.db.RelationalDB
import com.example.auto.dto.PhotoCollectionAction
import com.example.auto.dto.PhotoTask
import com.example.auto.dto.PhotoTaskStatus
import com.example.auto.dto.SubscriptionRequest
import com.example.auto.fakes.FakeRelationalDB
import com.example.auto.fakes.HttpServerFactory
import com.example.auto.http.SimpleHttpClient
import com.example.auto.server.AutoControlServerApp
import com.example.auto.server.IHttpServer
import groovy.json.JsonSlurper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import spock.lang.Specification

import java.util.concurrent.TimeUnit

class SubscriptionNotificationSpec extends Specification {

    // ZMIANA: Usunięto @Shared. Pola będą inicjalizowane w setup()
    AutoControlServerApp platzServer
    SimpleHttpClient platzClient
    MockWebServer webhookReceiverServer
    Map<String, PhotoCollectionAction> actionManifests

    def setup() {
        def fakeDbImpl = new FakeRelationalDB()
        def fakeHttpImpl = HttpServerFactory.create()
        actionManifests = [:]

        // === OSTATECZNA POPRAWKA: Rzutowanie na interfejsy ===
        RelationalDB dbInterface = fakeDbImpl
        IHttpServer httpInterface = fakeHttpImpl

        platzServer = new AutoControlServerApp(dbInterface, httpInterface, actionManifests)
        // =======================================================

        platzServer.start(0, "")
        def platzServerUrl = "http://localhost:${platzServer.getPort()}"
        platzClient = new SimpleHttpClient(platzServerUrl)

        webhookReceiverServer = new MockWebServer()
        webhookReceiverServer.start()
    }

    // ZMIANA: Logika przeniesiona z cleanupSpec() do cleanup()
    def cleanup() {
        platzServer?.stop()
        webhookReceiverServer?.shutdown()
    }


    def "should receive a notification when a subscribed action is completed"() {
        given: "a action is planned and a subscription is created"
        webhookReceiverServer.enqueue(new MockResponse().setResponseCode(200).setBody("Notification received"))

        def testTask = new PhotoTask(taskId: 'T1', status: PhotoTaskStatus.PENDING)
        def actionManifest = new PhotoCollectionAction(
                actionId: 'M-123',
                tasks: [testTask],
                status: "UPLOADED"
        )
        actionManifests['M-123'] = actionManifest

        def subRequest = new SubscriptionRequest(
                actionId: 'M-123',
                eventType: 'ACTION_COMPLETED',
                callbackUrl: webhookReceiverServer.url("/webhook-endpoint").toString()
        )
        def subResponse = platzClient.sendGenericPost("/subscriptions", subRequest)
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