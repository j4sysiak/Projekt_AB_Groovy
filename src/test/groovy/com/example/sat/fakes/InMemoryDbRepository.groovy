package com.example.sat.fakes

import com.example.sat.DataRepository

// Symuluje repozytorium oparte na bazie danych (inne zachowanie archiwizacji)
class InMemoryDbRepository implements DataRepository {
    Map<String, String> mainTable = [:]
    Map<String, String> archiveTable = [:]

    @Override
    void saveReprocessedData(String satelliteId, String data) {
        mainTable[satelliteId] = data
    }

    @Override
    boolean wasDataSaved(String satelliteId) {
        return mainTable.containsKey(satelliteId)
    }

    @Override
    String findDataById(String satelliteId) {
        return mainTable[satelliteId]
    }

    @Override
    void archiveData(String satelliteId, String dataToArchive) {
        println "ARCHIVING TO DB TABLE: INSERT INTO archive_data ..."
        // Symulujemy inne zachowanie - np. dodajemy prefiks do klucza w archiwum
        archiveTable["ARCHIVED_${satelliteId}"] = dataToArchive
    }
}