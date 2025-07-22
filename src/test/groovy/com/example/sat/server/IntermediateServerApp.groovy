package com.example.sat.server

import com.example.sat.fakes.FakeFileSystem
import com.example.sat.fakes.FakeRelationalDB
import com.example.sat.http.SimpleHttpClient
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import spark.Service

//Serwer SERW1  (pośrednik pomiędzy SAT i GRUND) obsługuje Potok2
class IntermediateServerApp {
    final FakeFileSystem fileSystem = new FakeFileSystem()
    final FakeRelationalDB database = new FakeRelationalDB()
    private Service http // Własna, prywatna instancja serwera Spark
    // POLE: Flaga do symulowania awarii w testach
    private boolean forceUnhealthy = false
    // POLE: Klient do komunikacji z serwerem GRUND
    private SimpleHttpClient grundClient

    void start(int port, String groundControlServerUrl = null) {
        println "SERVER (SERW1): Starting Intermediate Server on port ${port}..."
        if (groundControlServerUrl) {
            this.grundClient = new SimpleHttpClient(groundControlServerUrl)
        }

        http = Service.ignite() // Tworzymy nową, niezależną instancję serwera
        http.port(port)

        // Definiujemy endpointy na naszej lokalnej instancji 'http'

        http.get("/health") { req, res ->
            res.type("application/json")
            if (forceUnhealthy) {
                res.status(503) // Service Unavailable
                return JsonOutput.toJson([status: "DOWN", components: [database: "TIMEOUT"]])
            }
            res.status(200)
            return JsonOutput.toJson([status: "UP", components: [fileSystem: "OK", database: "OK"]])
        }

        http.post("/ingest") { req, res ->
            println "SERVER (SERW1): Receiving data package on /ingest endpoint."
            def jsonPayload = req.body()

            def fileName = "downlink_package_${System.currentTimeMillis()}.json"
            fileSystem.saveFile(fileName, jsonPayload)

            res.status(202) // Accepted
            return JsonOutput.toJson([status: "ACCEPTED", message: "Data package received and stored as ${fileName}"])
        }

        http.post("/process") { req, res ->
            archiveFilesToDatabase()
            res.status(200)
            return JsonOutput.toJson([status: "OK", archivedCount: database.photoTable.size()])
        }

        // ZMIANA: Endpoint /data jest już niepotrzebny, bo SERW1 będzie aktywnie wysyłał dane
        // Zastępujemy go endpointem /forward
        http.post("/forward") { req, res ->
            boolean success = forwardDataToGroundControl()
            res.type("application/json")
            if (success) {
                res.status(200)
                return JsonOutput.toJson([status: "OK", forwardedCount: database.photoTable.size()])
            } else {
                res.status(502) // Bad Gateway
                return JsonOutput.toJson([status: "ERROR", message: "Forwarding failed, Ground Control is unhealthy."])
            }
        }

        // === PRZYWRACAMY BRAKUJĄCY ENDPOINT ===
        http.get("/data") { req, res ->
            res.type("application/json")
            return JsonOutput.toJson(new ArrayList(database.getArchivedPhotos()))
        }

        http.awaitInitialization()
        println "SERVER (SERW1): Intermediate Server started on port ${http.port()}."
    }

    void stop() {
        if (http != null) {
            println "SERVER (SERW1): Stopping Intermediate Server..."
            http.stop()
            http.awaitStop()
            println "SERVER (SERW1): Server stopped."
        }
    }

    // METODA: Do sterowania stanem "zdrowia" w testach
    void forceHealthStatus(boolean isUnhealthy) {
        this.forceUnhealthy = isUnhealthy
    }

    // Publiczna metoda do pobierania portu
    int getPort() { return http.port() }

    // Prywatna metoda logiki biznesowej
    private void archiveFilesToDatabase() {
        println "SERVER (SERW1): Starting archiving process."
        def files = fileSystem.readAllFiles()
        files.each { fileName, jsonContent ->
            def photos = new JsonSlurper().parseText(jsonContent)
            photos.each { photoMap ->
                database.insertPhoto(photoMap)
            }
            fileSystem.deleteFile(fileName)
        }
    }

    void resetState() {
        this.fileSystem.files.clear()
        this.database.photoTable.clear()
        println "SERVER (SERW1): State has been reset."
    }

    // Metody do weryfikacji stanu wewnętrznego w testach
    int getDbPhotoCount() { return database.photoTable.size() }
    List<Map> getDbPhotos() { return database.getArchivedPhotos() }
    int getFileSystemFileCount() { return fileSystem.files.size() }

    // ZMIENIAMY METODĘ na publiczną i zwracającą status
    boolean forwardDataToGroundControl() {
        println "SERW1: Attempting to forward archived data to Ground Control."

        // === LOGIKA: Health Check ===
        println "SERW1: Performing health check on Ground Control server..."
        println "SERW1: Performing health check on Ground Control server..."
        def healthResponse = grundClient.getData("/health")
        if (healthResponse.statusCode != 200 || healthResponse.json.status != "UP") {
            println "SERW1: Health check FAILED for Ground Control. Aborting forward."
            return false // Przerywamy operację
        }
        println "SERW1: Health check PASSED."
        // ===================================

        def photos = database.getArchivedPhotos()
        if (photos) {
            // Zwracamy wynik zapytania
            def response = grundClient.sendGenericPost("/data/ingest", photos)
            // Zwracamy true tylko jeśli GRUND poprawnie przyjął dane
            return response.statusCode == 202
        }
        return true // Jeśli nie ma nic do wysłania, to też jest sukces
    }
}