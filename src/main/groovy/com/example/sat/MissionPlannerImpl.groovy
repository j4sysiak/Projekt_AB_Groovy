package com.example.sat

import com.example.sat.dto.PhotoCollectionManifest
import com.example.sat.dto.MissionRequest
import com.example.sat.dto.PhotoTask
import com.example.sat.exceptions.MissionPlanningException

class MissionPlannerImpl implements IMissionPlanner {
    private final IOrbitalMechanics orbitalMechanics
    private final ISatelliteStateProvider satelliteStateProvider

    MissionPlannerImpl(IOrbitalMechanics orbitalMechanics, ISatelliteStateProvider satelliteStateProvider) {
        this.orbitalMechanics = orbitalMechanics
        this.satelliteStateProvider = satelliteStateProvider
    }

    @Override
    PhotoCollectionManifest createPlan(MissionRequest request) throws MissionPlanningException {
        println "PLANNER: Received new mission request for target: ${request.targetCoordinates}"

        // Krok 1: Weryfikacja stanu satelity (bez zmian)
        def satelliteState = satelliteStateProvider.getCurrentState()
        if (satelliteState.batteryLevel < 30.0) {
            throw new MissionPlanningException("Cannot plan mission: Satellite power is too low (${satelliteState.batteryLevel}%).")
        }
        if (satelliteState.dataStorageAvailable < 1024) {
            throw new MissionPlanningException("Cannot plan mission: Insufficient data storage on satellite.")
        }

        // Krok 2: Weryfikacja orbity (bez zmian)
        def predictedPosition = orbitalMechanics.getCurrentPosition()
        if (Math.abs(predictedPosition.latitude - request.targetCoordinates.latitude) > 20.0) {
            throw new MissionPlanningException("Cannot plan mission: Target is outside of the satellite's orbital path.")
        }

        println "PLANNER: Mission is viable. Generating photo collection manifest."

        // KROK 3: Generowanie manifestu
        // ZMIANA: Tworzymy i zwracamy PhotoCollectionManifest
        def tasks = (1..5).collect { new PhotoTask() } // Na razie na sztywno 5 zdjęć
        def manifest = new PhotoCollectionManifest(tasks: tasks, status: "PLANNED")

        println "PLANNER: Manifest ${manifest.missionId} created successfully."
        return manifest
    }
}