package com.example.sat

import com.example.sat.fakes.ManualMockAlertingService
import com.example.sat.fakes.ManualMockConnection

class DataAnalysisFlowSpec extends SomeBaseTest { // Dziedziczymy, żeby mieć dostęp do `SYSTEM_CONFIGS`

    def "should raise an alert when an anomaly is detected in satellite data"() {
        given: "a service chain configured to download data containing a hurricane warning"
        def satelliteId = "SAT-STORM-01"
        def config = SYSTEM_CONFIGS["ALPHA"]

        // 1. Tworzymy ręczne mocki dla kluczowych, zmiennych elementów
        def connection = new ManualMockConnection()
        // Symulujemy, że satelita zwraca dane z ostrzeżeniem
        connection.dataToReturn = "<SatelliteData>HURRICANE CAT_5 DETECTED</SatelliteData>"

        def alertingService = new ManualMockAlertingService()

        // 2. Tworzymy prawdziwe implementacje dla reszty łańcucha
        def dataAnalyzer = new MeteorologicalDataAnalyzerImpl()
        // Używamy mocków Spocka dla prostszych zależności, które tylko trzeba "wyciszyć"
        def repository = Mock(DataRepository)
        def tokenCache = Mock(TokenCache)
        tokenCache.getToken() >> "VALID-TOKEN"
        def telemetryModule = Mock(TelemetryModule)
        def commandModule = Mock(CommandModule)
        def validator = Mock(InputValidator)
        def passPredictor = Mock(IPassPredictor)
        passPredictor.isCommunicationWindowOpen() >> true // Zawsze jest okno
        def positionVerifier = Mock(IPositionVerifier)

        // 3. Tworzymy nasz obiekt testowany
        def service = new ReprocessingServiceImpl(
                connection, repository, tokenCache, telemetryModule,
                commandModule, validator, passPredictor, positionVerifier,
                dataAnalyzer, alertingService
        )

        when: "the data is reprocessed"
        service.reprocessDataForSatellite(satelliteId)

        then: "a hurricane alert was raised"
        // Sprawdzamy stan naszego ręcznego mocka
        alertingService.alerts.size() == 1
        def alert = alertingService.alerts[0]
        alert.type == "HURRICANE"
        alert.message.contains("Hurricane detected")
    }
}