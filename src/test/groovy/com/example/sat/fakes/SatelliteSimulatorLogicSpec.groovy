// src/test/groovy/com/example/sat/fakes/SatelliteSimulatorLogicSpec.groovy
package com.example.sat.fakes

import com.example.sat.dto.PhotoCollectionManifest
import com.example.sat.dto.PhotoTask
import com.example.sat.dto.PhotoTaskStatus
import spock.lang.Specification

class SatelliteSimulatorLogicSpec extends Specification {

    // Testowany obiekt - nasz symulator
    SatelliteSimulator simulator

    // Metoda setup() jest uruchamiana przed każdym testem
    def setup() {
        // Tworzymy świeżą, czystą instancję symulatora dla każdego testu
        simulator = new SatelliteSimulator()
    }

    def "should correctly update task statuses after photo taking process"() {
        given: "a mission with tasks in different initial states"
        // Przygotowujemy manifest z mieszanką statusów
        def tasks = [
                new PhotoTask(taskId: "T1", status: PhotoTaskStatus.PENDING), // To zadanie powinno zostać wykonane
                new PhotoTask(taskId: "T2", status: PhotoTaskStatus.PENDING), // To też
                new PhotoTask(taskId: "T3", status: PhotoTaskStatus.TAKEN_AND_QUEUED), // To już jest zrobione
                new PhotoTask(taskId: "T4", status: PhotoTaskStatus.PENDING), // To zostanie wykonane
                new PhotoTask(taskId: "T5", status: PhotoTaskStatus.FAILED)   // Tego nie ruszamy
        ]
        simulator.activeManifest = new PhotoCollectionManifest(missionId: "M-LOGIC-TEST", tasks: tasks)

        when: "the photo taking process is simulated"
        simulator.simulatePhotoTakingProcess()

        then: "the simulator's photo storage contains the correct number of new photos"
        // Sprawdzamy, czy w "magazynie" zdjęć pojawiły się 3 nowe zdjęcia
        // (dla zadań T1, T2, T4)
        simulator.photoStorage.size() == 3

        and: "the statuses of the tasks in the manifest are correctly updated"
        // === UŻYCIE `count` DO ASERCJI ===

        // Zliczamy, ile zadań ma teraz status 'TAKEN_AND_QUEUED'.
        // Powinno być ich 4 (jedno oryginalne + trzy nowo wykonane).
        def queuedCount = simulator.activeManifest.tasks.count { it.status == PhotoTaskStatus.TAKEN_AND_QUEUED }
        queuedCount == 4

        // Zliczamy, ile zadań wciąż ma status 'PENDING'.
        // Powinno być 0, bo wszystkie zostały wykonane.
        def pendingCount = simulator.activeManifest.tasks.count { it.status == PhotoTaskStatus.PENDING }
        pendingCount == 0

        // Zliczamy, ile zadań ma status 'FAILED'.
        // Powinno być 1, bo ta metoda nie powinna dotykać zadań, które już miały błąd.
        def failedCount = simulator.activeManifest.tasks.count { it.status == PhotoTaskStatus.FAILED }
        failedCount == 1
    }

    def "should not take photos if battery level is too low"() {
        given: "a mission with pending tasks and low battery"
        def tasks = [
                new PhotoTask(taskId: "T1", status: PhotoTaskStatus.PENDING),
                new PhotoTask(taskId: "T2", status: PhotoTaskStatus.PENDING)
        ]
        simulator.activeManifest = new PhotoCollectionManifest(missionId: "M-BATTERY-TEST", tasks: tasks)

        // Symulujemy niski poziom baterii
        simulator.batteryLevel = 0.1 // Mniej niż wymagane 0.2

        when: "the photo taking process is simulated"
        simulator.simulatePhotoTakingProcess()

        then: "no new photos are created"
        simulator.photoStorage.isEmpty() // Magazyn powinien być pusty

        and: "all tasks remain in PENDING state"
        // === UŻYCIE `count` DO ASERCJI ===
        // Zliczamy, ile zadań jest wciąż w stanie PENDING. Powinny być wszystkie.
        def pendingCount = simulator.activeManifest.tasks.count { it.status == PhotoTaskStatus.PENDING }
        pendingCount == 2
    }
}