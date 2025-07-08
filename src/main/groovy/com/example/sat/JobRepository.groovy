package com.example.sat

// Repozytorium do zarządzania zadaniami reprocesowania
interface JobRepository {

    List<String> findPendingSatelliteIds()
    void markAsCompleted(String satelliteId)
    void markAsFailed(String satelliteId)
}