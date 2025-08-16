package com.example.sat

import com.example.sat.fakes.SatelliteSimulator
import com.example.sat.server.IntermediateServerApp
import com.example.sat.server.SatelliteSimulatorServer
import com.example.sat.http.SimpleHttpClient
import com.example.sat.dto.PhotoTaskStatus
import com.example.sat.dto.PhotoTask
import com.example.sat.dto.PhotoCollectionManifest
import com.example.sat.dto.PhotoData
import spock.lang.Specification

class HealthCheckSpec extends Specification {

    IntermediateServerApp intermediateServer
    SatelliteSimulatorServer satServer
    SatelliteSimulator simulator
    SimpleHttpClient satClient

    def setup() {
        // Serwer pośredniczący, do którego będziemy wysyłać dane
        intermediateServer = new IntermediateServerApp()
        intermediateServer.start(0, "") // Adres GRUND nie jest potrzebny
        def intermediateServerUrl = "http://localhost:${intermediateServer.getPort()}"

        // Symulator i serwer satelity, który wie, gdzie jest serwer pośredniczący
        simulator = new SatelliteSimulator()
        satServer = new SatelliteSimulatorServer(simulator, intermediateServerUrl)
        satServer.start(0)
        def satServerUrl = "http://localhost:${satServer.getPort()}"

        // Klient do komunikacji z serwerem satelity
        satClient = new SimpleHttpClient(satServerUrl)
    }

    def cleanup() {
        satServer?.stop()
        intermediateServer?.stop()
    }



    def "should successfully downlink data when intermediate server is healthy"() {
        given: "the satellite has one photo ready and the intermediate server is healthy"
        // Upewnijmy się, że serwer pośredniczący jest zdrowy
        intermediateServer.forceHealthStatus(false)

        // Przygotowujemy spójne dane w symulatorze
        def taskId = "HEALTHY-TASK-01"
        def missionId = "TEST-MISSION-1"

        def task = new PhotoTask(taskId: taskId, status: PhotoTaskStatus.TAKEN_AND_QUEUED)
        simulator.activeManifest = new PhotoCollectionManifest(missionId: missionId, tasks: [task])

        simulator.photoStorage[taskId] = new PhotoData(
                photoId: taskId,
                missionId: missionId,
                content: 'Healthy test data',
                timestamp: System.currentTimeMillis(),
                quality: 95 // POPRAWKA: Używamy liczby
        )

        when: "the downlink is triggered"
        def response = satClient.sendGenericPost("/downlink", [:])

        then: "the operation succeeds and data is transferred"
        response.statusCode == 200
        // Czekamy chwilę, bo zapis do pliku może być asynchroniczny
        Thread.sleep(500)
        intermediateServer.getFileSystemFileCount() == 1
    }



    def "should abort downlink when intermediate server is unhealthy"() {
        given: "the satellite has one photo ready but the intermediate server is unhealthy"
        // Ustawiamy serwer pośredniczący na "niezdrowy"
        intermediateServer.forceHealthStatus(true)

        // Przygotowujemy spójne dane w symulatorze
        def taskId = "UNHEALTHY-TASK-01"
        def missionId = "TEST-MISSION-2"

        def task = new PhotoTask(taskId: taskId, status: PhotoTaskStatus.TAKEN_AND_QUEUED)
        simulator.activeManifest = new PhotoCollectionManifest(missionId: missionId, tasks: [task])

        // POPRAWKA: Używamy spójnych ID i kompletnych danych
        simulator.photoStorage[taskId] = new PhotoData(
                photoId: taskId,
                missionId: missionId,
                content: 'Unhealthy test data',
                timestamp: System.currentTimeMillis(),
                quality: 98
        )

        when: "the downlink is triggered"
        def response = satClient.sendGenericPost("/downlink", [:])

        then: "the operation fails and no data is transferred"
        // Oczekujemy, że serwer satelity zwróci błąd, bo nie mógł dostarczyć danych
        response.statusCode == 502 // Bad Gateway - to jest odpowiedni status

        // Upewniamy się, że dane nie zostały usunięte z satelity
        simulator.photoStorage.size() == 1
        // I że nic nie trafiło do serwera pośredniczącego
        intermediateServer.getFileSystemFileCount() == 0
    }

}