package com.example.sat

import java.util.regex.Matcher
import java.util.regex.Pattern

class ReprocessingServiceImpl implements ISingleReprocessor {
    final SatelliteConnection connection
    final DataRepository repository
    final TokenCache tokenCache
    final TelemetryModule telemetryModule
    final CommandModule commandModule
    final InputValidator validator

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

    private void performPreflightChecks(CommandModule cmd) {
        println "SERVICE: Performing pre-flight checks..."
        String powerStatus = cmd.sendCommand("CHECK_POWER_STATUS")
        println "SERVICE DEBUG: Received power status response: '${powerStatus}'"

        if (powerStatus.contains("NACK")) {
            throw new IllegalStateException("Pre-flight check failed: Power system NACK. Status: ${powerStatus}")
        }

        // KLUCZOWA ZMIANA: Zamieniamy przecinek na kropkę PRZED parsowaniem
        String sanitizedPowerStatus = powerStatus.replace(',', '.')

        Pattern pattern = Pattern.compile("(\\d+\\.\\d*)") // Szukamy teraz jawnie kropki
        Matcher matcher = pattern.matcher(sanitizedPowerStatus)

        if (matcher.find()) {
            double parsedBatteryLevel = matcher.group(1).toDouble()
            println "SERVICE DEBUG: Parsed battery level: ${parsedBatteryLevel}"
            println "SERVICE DEBUG: Checking condition: (${parsedBatteryLevel} < 20.0)"

            if (parsedBatteryLevel < 20.0) {
                println "SERVICE DEBUG: Condition is TRUE. Throwing exception."
                throw new IllegalStateException("Pre-flight check failed: Insufficient power. Battery at ${parsedBatteryLevel}%.")
            }
        } else {
            println "SERVICE DEBUG: Could not parse status. Throwing exception."
            throw new IllegalStateException("Pre-flight check failed: Could not parse power status response: ${powerStatus}")
        }

        String healthStatus = cmd.sendCommand("CHECK_SYSTEM_HEALTH")
        if (healthStatus.contains("FAILED")) {
            throw new IllegalStateException("Pre-flight check failed: Critical component failure. Status: ${healthStatus}")
        }
        println "SERVICE: Pre-flight checks passed."
    }

    @Override
    boolean reprocessDataForSatellite(String satelliteId) {
        try {
            validator.validateSatelliteId(satelliteId)
        } catch (ValidationException e) {
            println "Error during reprocessing: ${e.message}"
            return false
        }

        telemetryModule.sendEvent("REPROCESSING_STARTED", [satelliteId: satelliteId])
        boolean connectedByThisService = false
        try {
            String token = tokenCache.getToken()
            connection.connect(token)
            connectedByThisService = true

            performPreflightChecks(commandModule)

            commandModule.sendCommand("SET_CAMERA_MODE:ACTIVE_IMAGING")
            String rawData = connection.fetchData(satelliteId)
            commandModule.sendCommand("SET_CAMERA_MODE:STANDBY")

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