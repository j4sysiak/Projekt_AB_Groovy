package com.example.sat

// Interfejs dla repozytorium danych (np. baza danych)
interface DataRepository  {
    void saveReprocessedData(String satelliteId, String data)
    boolean wasDataSaved(String satelliteId)
}