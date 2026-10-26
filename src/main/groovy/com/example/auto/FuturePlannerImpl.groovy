package com.example.auto

import com.example.auto.dto.PhotoCollectionAction
import com.example.auto.dto.ActionRequest
import com.example.auto.dto.PhotoTask
import com.example.auto.exceptions.ActionPlanningException

class FuturePlannerImpl implements IActionPlanner {
    private final IOrbitalMechanics orbitalMechanics
    private final IAutoStateProvider autoStateProvider

    FuturePlannerImpl(IOrbitalMechanics orbitalMechanics, IAutoStateProvider autoStateProvider) {
        this.orbitalMechanics = orbitalMechanics
        this.autoStateProvider = autoStateProvider
    }

    @Override
    PhotoCollectionAction createPlan(ActionRequest request) throws ActionPlanningException {
        println "PLANNER: Received new action request for target: ${request.targetCoordinates}"

        // Krok 1: Weryfikacja stanu (bez zmian)
        def autoState = autoStateProvider.getCurrentState()
        if (autoState.batteryLevel < 30.0) {
            throw new ActionPlanningException("Cannot plan action: Auto power is too low (${autoState.batteryLevel}%).")
        }
        if (autoState.dataStorageAvailable < 1024) {
            throw new ActionPlanningException("Cannot plan action: Insufficient data storage on auto.")
        }

        // Krok 2: Weryfikacja orbity (bez zmian)
        def predictedPosition = orbitalMechanics.getCurrentPosition()
        if (Math.abs(predictedPosition.latitude - request.targetCoordinates.latitude) > 20.0) {
            throw new ActionPlanningException("Cannot plan action: Target is outside of the auto's orbital path.")
        }

        println "PLANNER: Action is viable. Generating photo collection manifest."

        // KROK 3: Generowanie manifestu
        // ZMIANA: Tworzymy i zwracamy PhotoCollectionManifest
        def tasks = (1..5).collect { new PhotoTask() } // Na razie na sztywno 5 zdjęć
        def manifest = new PhotoCollectionAction(tasks: tasks, status: "PLANNED")

        println "PLANNER: Manifest ${manifest.actionId} created successfully."
        return manifest
    }
}