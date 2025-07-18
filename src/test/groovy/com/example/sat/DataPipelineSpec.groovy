package com.example.sat

import com.example.sat.fakes.*
import spock.lang.Specification

class DataPipelineSpec extends Specification {

    def "should successfully flow data from satellite, through intermediate server, to ground control"() {
        given: "a fully interconnected system of simulated servers"
        def simulator = new SatelliteSimulator()
        def satelliteServer = new FakeSatelliteServer(simulator: simulator)
        def groundServer = new FakeGroundControlServer(satelliteServer: satelliteServer)
        def intermediateServer = new IntermediateServer(groundControlServer: groundServer)

        when: "the full data pipeline is triggered with proper session management"
        // Etap 1: NAWIĄŻ POŁĄCZENIE (sesję)
        groundServer.connectToSatellite("VALID-TOKEN")

        // Etap 2: W ramach sesji, zleć zrobienie 2 zdjęć
        groundServer.requestPhotos(2, "HIGH")

        // Etap 3: ZAKOŃCZ POŁĄCZENIE
        groundServer.disconnectFromSatellite()

        // Etap 4: Satelita (już rozłączony) wysyła dane do SERW1 w dogodnym momencie
        satelliteServer.downlinkDataTo(intermediateServer)

        // Etap 5: SERW1 przetwarza i archiwizuje
        intermediateServer.archiveFilesToDatabase()

        // Etap 6: SERW1 wysyła dane do systemu naziemnego
        intermediateServer.forwardDataToGroundControl()

        then: "the data has correctly passed through all stages"
        // Weryfikujemy stan końcowy
        simulator.commandHistory.contains("TAKE_PHOTOS")
        simulator.photoQueue.isEmpty()
        intermediateServer.fileSystem.files.isEmpty()
        intermediateServer.database.photoTable.size() == 2
        groundServer.receivedPhotos.size() == 2
    }
}