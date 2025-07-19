Fantastycznie! To świetna wiadomość, że wszystko jest teraz uporządkowane i działa. Mamy solidny, spójny fundament, więc możemy bezpiecznie wrócić do naszego ekscytującego zadania – uruchomienia SatelliteSimulator jako osobnego, prawdziwego serwera HTTP.

Przypomnijmy nasz plan:

SatelliteSimulatorServer: Uruchomi prawdziwy serwer HTTP (używając biblioteki SparkJava), który będzie nasłuchiwał na określonym porcie. Endpointy tego serwera będą wołać metody naszego SatelliteSimulator.

HttpGroundControlClient: Stworzymy klienta HTTP (używając biblioteki groovy-wslite), który będzie wysyłał prawdziwe zapytania sieciowe do naszego serwera.

FullNetworkIntegrationSpec: Napiszemy test, który:

Uruchomi serwer przed wszystkimi testami.

Stworzy klienta, aby z nim rozmawiał.

Przeprowadzi pełny scenariusz komunikacji przez sieć (localhost).

Zatrzyma serwer po wszystkich testach.

Ponieważ już wcześniej przygotowaliśmy kod, teraz go tylko zweryfikujmy i upewnijmy się, że jest w pełni aktualny i spójny z resztą naszego projektu.

Krok 1: Upewnij się, że build.gradle jest gotowy
Sprawdź, czy masz dodane te zależności. Są one kluczowe.

Plik: build.gradle

Generated gradle
dependencies {
// ... wszystkie Twoje istniejące zależności ...

    // Biblioteki do obsługi serwera i klienta HTTP w testach
    testImplementation 'com.sparkjava:spark-core:2.9.4'
    testImplementation 'com.github.groovy-wslite:groovy-wslite:1.1.3'
    // SparkJava wymaga biblioteki do logowania, aby działać poprawnie
    testImplementation 'org.slf4j:slf4j-simple:1.7.32'
}
Use code with caution.
Gradle
Krok 2: Ostateczna Wersja Komponentów Sieciowych
Poniżej znajdują się finalne wersje kodu dla serwera i klienta. Wprowadziłem drobne poprawki, aby były w pełni zgodne z ostatnimi zmianami w SatelliteSimulator (np. obsługą parametrów w sendCommand).

Nowy katalog: src/test/groovy/com/example/sat/server

Plik: src/test/groovy/com/example/sat/server/SatelliteSimulatorServer.groovy

Generated groovy
package com.example.sat.server

import com.example.sat.fakes.SatelliteSimulator
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import spark.Spark

import static spark.Spark.*

class SatelliteSimulatorServer {
private final SatelliteSimulator simulator

    SatelliteSimulatorServer(SatelliteSimulator simulator) {
        this.simulator = simulator
    }

    void start(int port) {
        println "SERVER: Starting Satellite Simulator Server on port ${port}..."
        port(port)
        
        // POST /connect
        post("/connect") { req, res ->
            def authToken = req.headers("Authorization")?.replace("Bearer ", "")
            try {
                simulator.connect(authToken)
                res.status(200)
                return "Connected"
            } catch (Exception e) {
                res.status(400) // Bad Request
                return e.message
            }
        }

        // POST /command
        post("/command") { req, res ->
            res.type("application/json")
            try {
                def request = new JsonSlurper().parseText(req.body())
                def responseMessage = simulator.sendCommand(request.command, request.params ?: [:])
                
                def response = [
                    status: responseMessage.startsWith("ACK") ? "ACK" : "NACK",
                    message: responseMessage,
                    batteryLevel: simulator.batteryLevel
                ]
                return JsonOutput.toJson(response)
            } catch (Exception e) {
                res.status(500) // Internal Server Error
                return JsonOutput.toJson([status: "ERROR", message: e.message])
            }
        }
        
        // GET /downlink
        get("/downlink") { req, res ->
            res.type("application/json")
            def photos = simulator.downlinkQueuedPhotos()
            return JsonOutput.toJson(photos)
        }

        // POST /disconnect
        post("/disconnect") { req, res ->
            simulator.disconnect()
            res.status(200)
            return "Disconnected"
        }

        awaitInitialization()
        println "SERVER: Satellite Simulator Server started on port ${Spark.port()}."
    }

    void stop() {
        println "SERVER: Stopping Satellite Simulator Server..."
        Spark.stop()
        awaitStop()
        println "SERVER: Server stopped."
    }
}
Use code with caution.
Groovy
Plik: src/test/groovy/com/example/sat/fakes/HttpGroundControlClient.groovy

Generated groovy
package com.example.sat.fakes

import wslite.http.HTTPClient
import wslite.http.Response

class HttpGroundControlClient {
private final String serverUrl
private final HTTPClient httpClient = new HTTPClient()

    HttpGroundControlClient(String serverUrl) {
        this.serverUrl = serverUrl
    }
    
    Response connect(String authToken) {
        return httpClient.post(path: "${serverUrl}/connect") {
            headers.'Authorization' = "Bearer ${authToken}"
        }
    }

    Response disconnect() {
        return httpClient.post(path: "${serverUrl}/disconnect")
    }

    Response sendCommand(String command, Map params = [:]) {
        return httpClient.post(path: "${serverUrl}/command") {
            type "application/json"
            json = [command: command, params: params]
        }
    }

    Response downlinkData() {
        return httpClient.get(path: "${serverUrl}/downlink")
    }
}
Use code with caution.
Groovy
Krok 3: Ostateczny Test Integracji Sieciowej
Ten test połączy wszystko w całość. Upewnij się, że dodałeś metodę resetState() do SatelliteSimulator, aby zapewnić izolację testów.

W pliku SatelliteSimulator.groovy dodaj tę metodę:

Generated groovy
void resetState() {
this.batteryLevel = 95.0
this.cameraMode = "STANDBY"
this.connected = false
this.commandHistory = []
this.photoQueue = []
this.thrusterHealth = "NOMINAL"
this.powerOn = true
println "SIMULATOR: State has been reset."
}
Use code with caution.
Groovy
Nowy plik: src/test/groovy/com/example/sat/FullNetworkIntegrationSpec.groovy

Generated groovy
package com.example.sat

import com.example.sat.utils.http.SimpleHttpClient
import com.example.sat.fakes.SatelliteSimulator
import com.example.sat.server.SatelliteSimulatorServer
import spock.lang.Shared
import spock.lang.Specification
import spark.Spark

class FullNetworkIntegrationSpec extends Specification {

    // Używamy @Shared, aby serwer i klient były tworzone tylko raz dla całej klasy
    @Shared
    SatelliteSimulatorServer server
    @Shared
    HttpGroundControlClient client
    @Shared
    SatelliteSimulator simulator
    
    def setupSpec() {
        simulator = new SatelliteSimulator()
        server = new SatelliteSimulatorServer(simulator)
        server.start(0) // Uruchom na losowym wolnym porcie
        
        def serverUrl = "http://localhost:${Spark.port()}"
        client = new HttpGroundControlClient(serverUrl)
    }

    def cleanupSpec() {
        server.stop()
    }

    // cleanup jest wołane po każdym teście
    def cleanup() {
        simulator.resetState()
    }

    def "client should be able to execute a full photo mission over the network"() {
        given: "the simulator is in a healthy state"
        simulator.batteryLevel = 100.0
        def authToken = "SECRET-NETWORK-TOKEN"

        when: "the client executes a full mission sequence via HTTP"
        def connectResponse = client.connect(authToken)
        def commandResponse = client.sendCommand("TAKE_PHOTOS", [numberOfPhotos: 3, quality: "HIGH"])
        def downlinkResponse = client.downlinkData()
        def disconnectResponse = client.disconnect()

        then: "all network calls were successful"
        connectResponse.statusCode == 200
        commandResponse.statusCode == 200
        downlinkResponse.statusCode == 200
        disconnectResponse.statusCode == 200

        and: "the command was acknowledged and data was received"
        def commandJson = commandResponse.json
        commandJson.status == "ACK"
        commandJson.message.contains("3 photos taken")

        def photosJson = downlinkResponse.json as List
        photosJson.size() == 3
        photosJson[0].quality == "HIGH"
    }

    def "server should return a proper error if client tries to send command without connecting"() {
        when: "the client sends a command without connecting first"
        def commandResponse = client.sendCommand("CHECK_STATUS")

        then: "the server returns an error"
        commandResponse.statusCode == 500 // Internal Server Error
        
        and: "the error message indicates the problem"
        commandResponse.json.message.contains("Not connected")
    }
}
Use code with caution.
Groovy
Po dodaniu tych plików i uruchomieniu gradle clean build, powinieneś zobaczyć w logach, jak serwer startuje, a testy wykonują prawdziwe zapytania HTTP. To jest ostateczny dowód na solidność Twojej architektury.