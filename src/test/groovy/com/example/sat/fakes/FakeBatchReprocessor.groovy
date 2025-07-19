package com.example.sat.fakes
import com.example.sat.BatchReprocessingService

// To nie jest już mock, to jest "fake" - fałszywa, prosta implementacja
// Użyjemy dziedziczenia, żeby było prościej.
class FakeBatchReprocessor extends BatchReprocessingService {
    Map<String, Boolean> results = [:]
    Map<String, Integer> invocations = [:].withDefault{ 0 }

    FakeBatchReprocessor() {
        // Musimy podać fałszywy argument do konstruktora klasy nadrzędnej
        super(null)
    }

    @Override
    boolean processWithRetries(String satelliteId) {
        invocations[satelliteId]++
        // Zwróć zaplanowany wynik, domyślnie false
        return results.get(satelliteId, false)
    }
}