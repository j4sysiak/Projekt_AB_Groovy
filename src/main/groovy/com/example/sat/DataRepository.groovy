package com.example.sat

interface DataRepository {
    // Stare metody
    void saveReprocessedData(String satelliteId, String data)
    boolean wasDataSaved(String satelliteId)

    // Nowe metody
    String findDataById(String satelliteId)
    void archiveData(String satelliteId, String dataToArchive)
}