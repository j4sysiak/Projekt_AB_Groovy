// ścieżka: src/main/groovy/com/example/auto/server/AutoControlServerApp.groovy
package com.example.auto.server

import com.example.auto.db.RelationalDB
import com.example.auto.dto.PhotoCollectionAction
import com.example.auto.dto.PhotoData
import com.example.auto.dto.PhotoTask
import com.example.auto.dto.SubscriptionRequest
import com.example.auto.http.INotificationClient
import com.example.auto.http.IAutoApiClient
import com.example.auto.http.SimpleHttpClient
import com.example.auto.services.NotificationService
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.util.concurrent.Executors

/**
 * Główny serwer aplikacji, odpowiedzialny za zarządzanie misjami,
 * komunikację z  i wysyłanie powiadomień.
 *
 * Zależności (baza danych, serwer HTTP) są wstrzykiwane przez konstruktor,
 * co pozwala na łatwe testowanie i elastyczność.
 */
class AutoControlServerApp {
    // Pola są typowane interfejsami - klucz do czystej architektury
    private final IHttpServer http
    private final RelationalDB database
    private IAutoApiClient autoClient
    private NotificationService notificationService

    // Stan wewnętrzny serwera
    private Map<String, PhotoCollectionAction> actionManifests = [:]
    private boolean forceUnhealthy = false

    // === GŁÓWNY KONSTRUKTOR APLIKACYJNY ===
    AutoControlServerApp(RelationalDB database, IHttpServer httpServer) {
        this.database = database
        this.http = httpServer
        // Inicjalizujemy pustą mapę, jeśli nie została podana
        this.actionManifests = [:]
    }

    // === KONSTRUKTOR TESTOWY, KTÓREGO BRAKUJE ===
    // Pozwala on testom na wstrzyknięcie własnej, współdzielonej mapy manifestów.
    AutoControlServerApp(RelationalDB database, IHttpServer httpServer, Map actionManifests) {
        // Wywołujemy główny konstruktor
        this(database, httpServer)
        // Nadpisujemy mapę tą, którą otrzymaliśmy z testu
        this.actionManifests = actionManifests
    }

    void start(int port, String autoServerUrl) {
        this.autoClient = new SimpleHttpClient(autoServerUrl)
        this.http.port(port)

        // Konfiguracja serwisu powiadomień
        def notificationExecutor = Executors.newCachedThreadPool()
        def notificationClient = { String url, Object payload ->
            notificationExecutor.submit {
                try {
                    new SimpleHttpClient().send(url, payload)
                } catch (Exception e) {
                    println "NOTIFICATION_CLIENT_ERROR: Failed to send notification: ${e.message}"
                }
            }
        } as INotificationClient

        // this.database jest typu RelationalDB
        // notificationClient jest typu INotificationClient
        this.notificationService = new NotificationService(this.database, notificationClient)

        // --- Definicja wszystkich endpointów API ---
        defineEndpoints()

        http.awaitInitialization()

        // ZMIANA: Użyjmy portu, który już znamy, zamiast pytać serwer
        println "SERVER (PLATZ): Started on port ${port}."
    }

    private void defineEndpoints() {

        http.get("/health") { req, res ->
            res.type("application/json")
            if (forceUnhealthy) {
                res.status(503)
                return JsonOutput.toJson([status: "DEGRADED", reason: "Forced unhealthy state"])
            }
            res.status(200)
            return JsonOutput.toJson([status: "UP"])
        }

        http.get("/") { req, res ->
            res.type("application/json")
            String baseUrl = req.scheme() + "://" + req.host()
            return JsonOutput.toJson([
                    _links: [
                            self: [href: baseUrl + "/"],
                            "create-action": [href: baseUrl + "/action/photo-collection", method: "POST"],
                            "create-subscription": [href: baseUrl + "/subscriptions", method: "POST"]
                    ]
            ])
        }

        http.post("/subscriptions") { req, res ->
            res.type("application/json")
            try {
                def subRequest = new SubscriptionRequest(new JsonSlurper().parseText(req.body()))
                database.saveSubscription(subRequest)
                res.status(201)
                return JsonOutput.toJson([status: "SUBSCRIPTION_CREATED", details: subRequest])
            } catch (Exception e) {
                res.status(400)
                return JsonOutput.toJson([status: "ERROR", message: e.message])
            }
        }

        http.post("/action/photo-collection") { req, res ->
            res.type("application/json")
            try {
                def actionData = new JsonSlurper().parseText(req.body())
                int photoCount = actionData.photoCount as int

                def tasks = (1..photoCount).collect { new PhotoTask() }
                def action = new PhotoCollectionAction(tasks: tasks)
                actionManifests[action.actionId] = action
                println "PLATZ: New action ${action.actionId} created with status PLANNED."

                res.status(202) // Accepted
                return JsonOutput.toJson(action)
            } catch (Exception e) {
                res.status(500)
                return JsonOutput.toJson([error: "Unexpected error: ${e.message}"])
            }
        }

        http.get("/action/:actionId") { req, res ->
            res.type("application/json")
            String actionId = req.params(":actionId")
            def action = actionManifests[actionId]

            if (!action) {
                res.status(404)
                return JsonOutput.toJson([error: "Action with ID ${actionId} not found."])
            }

            String baseUrl = req.scheme() + "://" + req.host()
            def actionUrl = baseUrl + "/action/${actionId}"
            def response = [
                    actionId: action.actionId,
                    status: action.status,
                    _links: [
                            self: [href: actionUrl],
                            tasks: [href: actionUrl + "/tasks"]
                    ]
            ]

            switch (action.status) {
                case "PLANNED":
                    response._links."accept-action" = [href: actionUrl + "/accept", method: "POST"]
                    break
                case "ACCEPTED":
                    response._links."upload-to-auto" = [href: actionUrl + "/upload", method: "POST"]
                    break
                case "COMPLETED":
                    response._links."create-subscription" = [href: baseUrl + "/subscriptions", method: "POST"]
                    break
            }

            res.status(200)
            return JsonOutput.toJson(response)
        }

        http.get("/action/:actionId/tasks") { req, res ->
            res.type("application/json")
            String actionId = req.params(":actionId")
            def manifest = actionManifests[actionId]

            if (!manifest) { res.status(404); return JsonOutput.toJson([error: "Action not found."])}

            int page = req.queryParams("page") ? req.queryParams("page") as int : 1
            int size = req.queryParams("size") ? req.queryParams("size") as int : 10
            int totalTasks = manifest.tasks.size()
            int totalPages = Math.ceil(totalTasks / (double) size) as int
            int offset = (page - 1) * size
            def tasksOnPage = manifest.tasks.drop(offset).take(size)

            String baseUrl = req.scheme() + "://" + req.host()
            String tasksUrl = baseUrl + "/action/${actionId}/tasks"
            def links = [self: [href: tasksUrl + "?page=${page}&size=${size}"]]
            if (page > 1) links.prev = [href: tasksUrl + "?page=${page - 1}&size=${size}"]
            if (page < totalPages) links.next = [href: tasksUrl + "?page=${page + 1}&size=${size}"]
            links.first = [href: tasksUrl + "?page=1&size=${size}"]
            links.last = [href: tasksUrl + "?page=${totalPages}&size=${size}"]

            def responseBody = [
                    _links: links,
                    page: [
                            size: tasksOnPage.size(), totalElements: totalTasks,
                            totalPages: totalPages, number: page
                    ],
                    _embedded: [
                            tasks: tasksOnPage.collect { [taskId: it.taskId, status: it.status.name()] }
                    ]
            ]
            return JsonOutput.toJson(responseBody)
        }

        http.post("/action/:actionId/accept") { req, res ->
            res.type("application/json")
            String actionId = req.params(":actionId")
            def manifest = actionManifests[actionId]

            if (!manifest || manifest.status != "PLANNED") {
                res.status(409)
                return JsonOutput.toJson([error: "Action cannot be accepted at this time."])
            }
            manifest.status = "ACCEPTED"
            res.status(200)
            return JsonOutput.toJson([status: "OK", message: "Action ${actionId} has been accepted."])
        }

        http.post("/action/:actionId/upload") { req, res ->
            res.type("application/json")
            String actionId = req.params(":actionId")
            def manifest = actionManifests[actionId]

            if (!manifest || manifest.status != "ACCEPTED") {
                res.status(409)
                return JsonOutput.toJson([error: "Action cannot be uploaded at this time."])
            }

            manifest.status = "UPLOADED"
            def uploadResponse = autoClient.uploadActionPlan(manifest)

            if (uploadResponse.statusCode == 202) {
                res.status(200)
                return JsonOutput.toJson([status: "OK", message: "Action ${actionId} uploaded successfully."])
            } else {
                manifest.status = "FAILED"
                res.status(502)
                return JsonOutput.toJson([status: "ERROR", message: "Failed to upload action to auto."])
            }
        }

        http.post("/data/ingest") { req, res ->
            res.type("application/json")
            try {
                def rawList = new JsonSlurper().parseText(req.body()) as List<Map>

                // ZMIANA: Zamiast new PhotoData(it), tworzymy obiekt jawnie
                def photos = rawList.collect { map ->
                    new PhotoData(
                            photoId:   map.photoId,
                            actionId: map.actionId,
                            timestamp: map.timestamp,
                            quality:   map.quality,
                            content:   map.content
                    )
                }

                println "==== PLATZ DEBUG /data/ingest: START ===="
                def body = req.body()
                println "DEBUG: Otrzymane ciało: ${body}"

                //def photos = (new JsonSlurper().parseText(body) as List<Map>).collect { new PhotoData(it) }
                println "DEBUG: Sparsowano ${photos.size()} obiektów PhotoData."

                photos.each { database.insertPhoto(it) }
                println "DEBUG: Zapisano zdjęcia do bazy."

                def actionId = photos.first()?.actionId
                println "DEBUG: actionId z pierwszej fotki: ${actionId}"

                if (actionId) {
                    println "DEBUG: Sprawdzam, czy misja jest kompletna. Mapa manifestów ma rozmiar: ${actionManifests.size()}"
                    def manifestExists = actionManifests.containsKey(actionId)
                    println "DEBUG: Czy manifest dla ${actionId} istnieje w mapie? ${manifestExists}"

                    if (isActionComplete(actionId)) {
                        println "PLATZ: Action ${actionId} is complete. Publishing event."
                        notificationService.publishEvent(
                                "ACTION_COMPLETED", actionId,
                                [photoCount: getPhotoCountForAction(actionId), message: "All data processed."]
                        )
                    } else {
                        println "DEBUG: isActionComplete zwróciło false."
                    }
                }
                println "==== PLATZ DEBUG /data/ingest: KOŃCZĘ SUKCESEM ===="
                res.status(202)
                return JsonOutput.toJson([status: "DATA_ACCEPTED", ingestedCount: photos.size()])
            } catch (Exception e) {
                println "==== PLATZ DEBUG /data/ingest: ZŁAPANO WYJĄTEK! ===="
                println "Błąd: ${e.class.name} - ${e.message}"
                e.printStackTrace(new PrintWriter(System.out))
                println "==== PLATZ DEBUG /data/ingest: KONIEC Z BŁĘDEM ===="
                res.status(500)
                return JsonOutput.toJson([status: "ERROR", message: e.message])
            }
        }
    }

    // === Metody Pomocnicze i Zarządzające ===

    void stop() { http?.stop() }
    int getPort() { return http.getPort() }
    void forceHealthStatus(boolean isUnhealthy) { this.forceUnhealthy = isUnhealthy }

    // Metoda do aktualizacji URL-a klienta AUTO po starcie
    void updateAutoClientUrl(String checkerUrl) {
        if (checkerUrl) {
            this.autoClient = new SimpleHttpClient(checkerUrl)
            println "PLATZ: Auto client URL updated to ${checkerUrl}"
        }
    }

    void resetState() {
        actionManifests.clear()
        database.clearPhotos()
        database.clearSubscriptions()
        forceUnhealthy = false
        println "SERVER (PLATZ): State has been reset."
    }

    int getDbPhotoCount() { return database.getPhotoCount() }

    private boolean isActionComplete(String actionId) {
        def manifest = actionManifests[actionId]
        if (!manifest) return false

        def expectedCount = manifest.tasks.size()
        def actualCount = getPhotoCountForAction(actionId)

        println "ACTION_CHECK (${actionId}): Expected=${expectedCount}, Actual=${actualCount}"
        return actualCount >= expectedCount
    }

    private int getPhotoCountForAction(String actionId) {
        return database.getPhotoCountForAction(actionId)
    }
}