package com.example.auto.fakes
import com.example.auto.BatchReprocessingService

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
    boolean processWithRetries(String autoId) {
        invocations[autoId]++
        // Zwróć zaplanowany wynik, domyślnie false
        return results.get(autoId, false)
    }
}