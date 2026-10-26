package com.example.auto.fakes

import com.example.auto.DataRepository

// Symuluje repozytorium oparte na bazie danych (inne zachowanie archiwizacji)
class InMemoryDbRepository implements DataRepository {
    Map<String, String> mainTable = [:]
    Map<String, String> archiveTable = [:]

    @Override
    void saveReprocessedData(String autoId, String data) {
        mainTable[autoId] = data
    }

    @Override
    boolean wasDataSaved(String autoId) {
        return mainTable.containsKey(autoId)
    }

    @Override
    String findDataById(String autoId) {
        return mainTable[autoId]
    }

    @Override
    void archiveData(String autoId, String dataToArchive) {
        println "ARCHIVING TO DB TABLE: INSERT INTO archive_data ..."
        archiveTable["ARCHIVED_${autoId}"] = dataToArchive
    }
}