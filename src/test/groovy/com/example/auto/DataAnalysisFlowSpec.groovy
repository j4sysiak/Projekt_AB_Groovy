package com.example.auto

import com.example.auto.fakes.ManualMockAlertingService
import com.example.auto.fakes.ManualMockConnection

class DataAnalysisFlowSpec extends SomeBaseTest { // Dziedziczymy, żeby mieć dostęp do `SYSTEM_CONFIGS`

    def "Should raise an alert when an anomaly is detected data"() {
        given: "a service chain configured to download data containing a hurricane warning"
        def autoId = "AUTO-STORM-01"
        def config = SYSTEM_CONFIGS["ALPHA"]

        // 1. Tworzymy ręczne mocki dla kluczowych, zmiennych elementów
        def connection = new ManualMockConnection()
        connection.dataToReturn = "<AutoData>HURRICANE CAT_5 DETECTED</AutoData>"

        def alertingService = new ManualMockAlertingService()

        // 2. Tworzymy prawdziwe implementacje dla reszty łańcucha
        //    Mocki Spocka dla "tła" - teraz z PEŁNĄ konfiguracją
        def repository = Mock(DataRepository)
        def tokenCache = Mock(TokenCache)
        def downloadModule = Mock(DownloadModule)
        def commandModule = Mock(CommandModule)
        def validator = Mock(InputValidator)
        def passPredictor = Mock(IPassPredictor)
        def positionVerifier = Mock(IPositionVerifier)

        // === KONFIGURACJA ZACHOWANIA MOCKÓW ===
        // Musimy zasymulować "happy path" dla wszystkich bramek kontrolnych
        passPredictor.isCommunicationWindowOpen() >> true // Zawsze jest okno
        positionVerifier.verifyPosition(autoId) >> { /* Nic nie robi, czyli przechodzi */ }
        validator.validateAutoId(autoId) >> { /* Nic nie robi, czyli przechodzi */ }
        tokenCache.getToken() >> "VALID-TOKEN"

        // Mówimy commandModule, jak ma odpowiadać na zapytania o status
        commandModule.sendCommand("CHECK_POWER_STATUS", _) >> "STATUS: Battery: 95.00%, Panels: CHARGING"
        commandModule.sendCommand("CHECK_SYSTEM_HEALTH", _) >> "STATUS: Thrusters: NOMINAL"

        // 3. Prawdziwa implementacja analizatora, bo jego logikę testujemy pośrednio
        def dataAnalyzer = new MeteorologicalDataAnalyzerImpl()

        // 4. Tworzymy nasz obiekt testowany
        def service = new ReprocessingServiceImpl(
                connection,
                repository,
                tokenCache,
                downloadModule,
                commandModule,
                validator,
                passPredictor,
                positionVerifier,
                dataAnalyzer,
                alertingService
        )

        when: "the data is reprocessed"
        def result = service.reprocessDataForAuto(autoId)

        then: "a hurricane alert was raised"
        result
        alertingService.alerts.size() == 1
        def alert = alertingService.alerts[0]
        alert.type == "HURRICANE"
        alert.message.contains("Hurricane detected")

        and: "the successful command sequence was sent"
        // Możemy teraz dodać weryfikację interakcji z mockami Spocka
        1 * commandModule.sendCommand("SET_CAMERA_MODE:ACTIVE_IMAGING", _)
        1 * commandModule.sendCommand("SET_CAMERA_MODE:STANDBY", _)
        1 * downloadModule.sendEvent("REPROCESSING_SUCCESS", [
                autoId: autoId,
                dataSize: "PROCESSED: [${connection.dataToReturn}]".length()
        ])
    }
}