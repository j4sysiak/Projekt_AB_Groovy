package com.example.auto

interface DataRepository {
    // Stare metody
    void saveReprocessedData(String autoId, String data)
    boolean wasDataSaved(String autoId)

    // Nowe metody
    String findDataById(String autoId)
    void archiveData(String autoId, String dataToArchive)
}