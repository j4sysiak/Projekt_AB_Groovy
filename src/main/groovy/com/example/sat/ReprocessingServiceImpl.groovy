package com.example.sat

import java.util.regex.Matcher
import java.util.regex.Pattern

class ReprocessingServiceImpl implements ISingleReprocessor {
    final SatelliteConnection connection
    final DataRepository repository
    final TelemetryModule telemetryModule
    final CommandModule commandModule

    // ZMIANA: Zależność od TokenCache
    final TokenCache tokenCache
    // private final String apiKey
    // final AuthService authService

    // Flaga tylko do celów testowych, aby wymusić błąd
       public boolean forceConnectionFailure = false

    // NOWA ZALEŻNOŚĆ
    final InputValidator validator

    // Aktualizujemy konstruktor
    ReprocessingServiceImpl(
            SatelliteConnection connection, DataRepository repository,
            TokenCache tokenCache, TelemetryModule telemetryModule,
            CommandModule commandModule, InputValidator validator) {
        this.connection = connection
        this.repository = repository
        this.tokenCache = tokenCache
        this.telemetryModule = telemetryModule
        this.commandModule = commandModule
        this.validator = validator
    }

    // NOWA, prywatna metoda do sprawdzania stanu
    private void performPreflightChecks(CommandModule cmd) {
        println "SERVICE: Performing pre-flight checks..."

        // --- Sprawdzanie Zasilania ---
        def powerStatus = cmd.sendCommand("CHECK_POWER_STATUS")
        if (powerStatus.contains("NACK")) {
            throw new IllegalStateException("Pre-flight check failed: Power system NACK. Status: ${powerStatus}")
        }

        // Używamy wyrażenia regularnego, aby bezpiecznie wyciągnąć liczbę
        Pattern pattern = Pattern.compile("(\\d+\\.?\\d*)%")
        Matcher matcher = pattern.matcher(powerStatus)

        if (matcher.find()) {
            double batteryLevel = matcher.group(1).toDouble()
            if (batteryLevel < 20.0) {
                throw new IllegalStateException("Pre-flight check failed: Insufficient power. Battery at ${batteryLevel}%.")
            }
        } else {
            throw new IllegalStateException("Pre-flight check failed: Could not parse power status response: ${powerStatus}")
        }

        // --- Sprawdzanie Stanu Podzespołów ---
        String healthStatus = cmd.sendCommand("CHECK_SYSTEM_HEALTH")
        if (healthStatus.contains("FAILED")) {
            throw new IllegalStateException("Pre-flight check failed: Critical component failure. Status: ${healthStatus}")
        }

        println "SERVICE: Pre-flight checks passed."
    }

    // Implementujemy metodę z interfejsu
    @Override
    boolean reprocessDataForSatellite(String satelliteId) {

        // ZMIANA: Walidacja jako PIERWSZY KROK
        try {
            validator.validateSatelliteId(satelliteId)
        } catch (ValidationException e) {
            println "Error during reprocessing: ${e.message}"
            // Nie wysyłamy nawet telemetrii, bo to błąd "śmieciowych" danych wejściowych
            return false // Zawieś działanie natychmiast
        }

        telemetryModule.sendEvent("REPROCESSING_STARTED", [satelliteId: satelliteId])
        boolean connectedByThisService = false
        try {
            // ZMIANA: Pobieramy token z cache'u, a nie bezpośrednio z AuthService
            String token = tokenCache.getToken()
            //String token = authService.getAuthToken(apiKey)

            // KLUCZOWA ZMIANA: Ręcznie symulujemy błąd, jeśli flaga jest ustawiona
            if (forceConnectionFailure) {
                throw new IllegalStateException("Forced connection failure for testing")
            }

            connection.connect(token)
            connectedByThisService = true

            // ZMIANA: Wykonujemy sprawdzenie stanu przed główną operacją
            performPreflightChecks(commandModule)

            // Wysyłamy sekwencję bardziej realistycznych komend
            commandModule.sendCommand("SET_CAMERA_MODE:ACTIVE_IMAGING")
            String rawData = connection.fetchData(satelliteId)
            commandModule.sendCommand("SET_CAMERA_MODE:STANDBY") // Wyłączamy aparat po zrobieniu zdjęcia

            String processedData = "PROCESSED: [${rawData}]"
            repository.saveReprocessedData(satelliteId, processedData)
            telemetryModule.sendEvent("REPROCESSING_SUCCESS", [satelliteId: satelliteId, dataSize: processedData.length()])
            return true
        } catch (Exception e) {
            telemetryModule.sendEvent("REPROCESSING_FAILURE", [satelliteId: satelliteId, error: e.message])
            println "Error during single reprocessing attempt: ${e.message}"
            return false
        } finally {
            if (connectedByThisService) {
                connection.disconnect()
            }
        }
    }
}