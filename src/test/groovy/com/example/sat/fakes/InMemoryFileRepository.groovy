package com.example.sat.fakes

import com.example.sat.DataRepository

// Symuluje repozytorium oparte na systemie plików (proste mapy)
class InMemoryFileRepository implements DataRepository {
    Map<String, String> fileSystem = [:]
    Map<String, String> archive = [:]

    @Override
    void saveReprocessedData(String satelliteId, String data) {
        fileSystem[satelliteId] = data
    }

    @Override
    boolean wasDataSaved(String satelliteId) {
        return fileSystem.containsKey(satelliteId)
    }

    @Override
    String findDataById(String satelliteId) {
        return fileSystem[satelliteId]
    }

    @Override
    void archiveData(String satelliteId, String dataToArchive) {
        println "ARCHIVING TO FILE: archive/${satelliteId}.bak"
        archive[satelliteId] = dataToArchive
    }
}