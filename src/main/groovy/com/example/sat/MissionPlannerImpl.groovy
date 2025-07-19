package com.example.sat

import com.example.sat.dto.MissionPlan
import com.example.sat.dto.MissionRequest

class MissionPlannerImpl implements IMissionPlanner {
    // Planista musi mieć dostęp do wiedzy o orbicie i stanie satelity
    private final IOrbitalMechanics orbitalMechanics
    private final ISatelliteStateProvider satelliteStateProvider // Nowy interfejs

    MissionPlannerImpl(IOrbitalMechanics orbitalMechanics, ISatelliteStateProvider satelliteStateProvider) {
        this.orbitalMechanics = orbitalMechanics
        this.satelliteStateProvider = satelliteStateProvider
    }

    @Override
    MissionPlan createPlan(MissionRequest request) throws MissionPlanningException {
        println "PLANNER: Received new mission request for target: ${request.targetCoordinates}"

        // KROK 1: Weryfikacja stanu satelity
        def satelliteState = satelliteStateProvider.getCurrentState()
        if (satelliteState.batteryLevel < 30.0) {
            throw new MissionPlanningException("Cannot plan mission: Satellite power is too low (${satelliteState.batteryLevel}%).")
        }
        if (satelliteState.dataStorageAvailable < 1024) { // w MB
            throw new MissionPlanningException("Cannot plan mission: Insufficient data storage on satellite.")
        }

        // KROK 2: Weryfikacja orbity (bardzo uproszczona symulacja)
        // Sprawdzamy, czy w ogóle jest szansa, że satelita będzie w pobliżu.
        def predictedPosition = orbitalMechanics.getCurrentPosition() // W realnym systemie przewidywalibyśmy przyszłą pozycję
        if (Math.abs(predictedPosition.latitude - request.targetCoordinates.latitude) > 20.0) {
            throw new MissionPlanningException("Cannot plan mission: Target is outside of the satellite's orbital path.")
        }

        println "PLANNER: Mission is viable. Generating command sequence."

        // KROK 3: Generowanie planu
        def plan = new MissionPlan(
                missionId: "MPLAN-" + System.nanoTime(),
                status: "PENDING_UPLOAD",
                estimatedExecutionTime: request.startTimeMillis,
                commandSequence: [
                        [command: "ORIENT_TO_COORDS", params: [lat: request.targetCoordinates.latitude, lon: request.targetCoordinates.longitude]],
                        [command: "TAKE_PHOTOS", params: [numberOfPhotos: 5, quality: "HIGH"]],
                        [command: "ORIENT_TO_NADIR"] // Powrót do domyślnej orientacji
                ]
        )

        println "PLANNER: Mission plan ${plan.missionId} created successfully."
        return plan
    }
}