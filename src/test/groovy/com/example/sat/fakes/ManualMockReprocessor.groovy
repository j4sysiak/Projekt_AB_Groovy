package com.example.sat.fakes

import com.example.sat.ISingleReprocessor

// To jest nasz własny, ręcznie napisany mock. Jest w 100% niezawodny.
class ManualMockReprocessor implements ISingleReprocessor {

    // Ustawiamy z góry, co ma zwracać przy kolejnych wywołaniach
    List<Boolean> resultsToReturn = []

    // Licznik wywołań
    int invocationCount = 0

    @Override
    boolean reprocessDataForSatellite(String satelliteId) {
        invocationCount++

        // Jeśli mamy zaplanowane wyniki, zwracamy kolejny z nich.
        if (invocationCount <= resultsToReturn.size()) {
            return resultsToReturn[invocationCount - 1]
        }

        // Domyślnie zwracaj false, jeśli skończą się zaplanowane wyniki.
        return false
    }
}