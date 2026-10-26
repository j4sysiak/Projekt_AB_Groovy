package com.example.auto.fakes

import com.example.auto.DataRepository

// Symuluje repozytorium oparte na systemie plików (proste mapy)
class InMemoryFileRepository implements DataRepository {
    Map<String, String> fileSystem = [:]
    Map<String, String> archive = [:]

    @Override
    void saveReprocessedData(String autoId, String data) {
        fileSystem[autoId] = data
    }

    @Override
    boolean wasDataSaved(String autoId) {
        return fileSystem.containsKey(autoId)
    }

    @Override
    String findDataById(String autoId) {
        return fileSystem[autoId]
    }

    @Override
    void archiveData(String autoId, String dataToArchive) {
        println "ARCHIVING TO FILE: archive/${autoId}.bak"
        archive[autoId] = dataToArchive
    }
}