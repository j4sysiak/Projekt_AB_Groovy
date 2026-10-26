package com.example.auto

import com.example.auto.domain.AnalysisResult
import com.example.auto.exceptions.PositionMismatchException
import com.example.auto.exceptions.ValidationException

import java.util.regex.Matcher
import java.util.regex.Pattern

class ReprocessingServiceImpl implements ISingleReprocessor {
    final AutoConnection connection
    final DataRepository repository
    final TokenCache tokenCache
    final DownloadModule downloadModule
    final CommandModule commandModule
    final InputValidator validator
    final IPassPredictor passPredictor
    final IPositionVerifier positionVerifier
    final IDataAnalyzer dataAnalyzer
    final IAlertingService alertingService

    ReprocessingServiceImpl(
            AutoConnection connection,
            DataRepository repository,
            TokenCache tokenCache,
            DownloadModule downloadModule,
            CommandModule commandModule,
            InputValidator validator,
            IPassPredictor passPredictor,
            IPositionVerifier positionVerifier,
            IDataAnalyzer dataAnalyzer,
            IAlertingService alertingService) {
        this.connection = connection
        this.repository = repository
        this.tokenCache = tokenCache
        this.downloadModule = downloadModule
        this.commandModule = commandModule
        this.validator = validator
        this.passPredictor = passPredictor
        this.positionVerifier = positionVerifier
        this.dataAnalyzer = dataAnalyzer
        this.alertingService = alertingService
    }

    private void performPreflightChecks(CommandModule cmd) {
        println "SERVICE: Performing pre-flight checks..."
        String powerStatus = cmd.sendCommand("CHECK_POWER_STATUS", [:])
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

        String healthStatus = cmd.sendCommand("CHECK_SYSTEM_HEALTH", [:])
        if (healthStatus.contains("FAILED")) {
            throw new IllegalStateException("Pre-flight check failed: Critical component failure. Status: ${healthStatus}")
        }
        println "SERVICE: Pre-flight checks passed."
    }

    @Override
    boolean reprocessDataForAuto(String autoId) {
        // KROK 0: SPRAWDZENIE OKNA KOMUNIKACYJNEGO
        if (!passPredictor.isCommunicationWindowOpen()) {
            println "Error during reprocessing: No communication window available at this time."
            // Możemy tu też wysłać specjalny event
            downloadModule.sendEvent("NO_COMMUNICATION_WINDOW", [autoId: autoId])
            return false
        }

        // KROK 0.5: WERYFIKACJA POZYCJI
        try {
            positionVerifier.verifyPosition(autoId)
        } catch (PositionMismatchException e) {
            println "Error during reprocessing: ${e.message}"
            // Wyślijmy alert, że model orbitalny wymaga korekty
            downloadModule.sendEvent("MODEL_DRIFT_DETECTED", [autoId: autoId, details: e.message])
            return false
        }

        try {
            validator.validateAutoId(autoId)
        } catch (ValidationException e) {
            println "Error during reprocessing: ${e.message}"
            return false
        }

        downloadModule.sendEvent("REPROCESSING_STARTED", [autoId: autoId])
        boolean connectedByThisService = false
        try {
            String token = tokenCache.getToken()
            connection.connect(token)
            connectedByThisService = true

            performPreflightChecks(commandModule)

            commandModule.sendCommand("SET_CAMERA_MODE:ACTIVE_IMAGING", [:])
            String rawData = connection.fetchData(autoId)
            commandModule.sendCommand("SET_CAMERA_MODE:STANDBY", [:])

            String processedData = "PROCESSED: [${rawData}]"
            repository.saveReprocessedData(autoId, processedData)

            downloadModule.sendEvent("REPROCESSING_SUCCESS", [autoId: autoId, dataSize: processedData.length()])

            // === NOWA LOGIKA: ANALIZA I ALARMOWANIE ===
            println "SERVICE: Initiating post-processing data analysis..."
            AnalysisResult analysisResult = dataAnalyzer.analyze(rawData)
            if (analysisResult.anomalyDetected) {
                alertingService.raiseAlert(analysisResult.anomalyType, analysisResult.details)
            }

            return true
        } catch (Exception e) {
            downloadModule.sendEvent("REPROCESSING_FAILURE", [autoId: autoId, error: e.message])
            println "Error during single reprocessing attempt: ${e.message}"
            return false
        } finally {
            if (connectedByThisService) {
                connection.disconnect()
            }
        }
    }
}