package com.example.sat

// Interfejs do zarządzania zadaniami reprocesowania
interface JobRepository {
    // Znajduje ID satelitów, które czekają na przetworzenie
    List<String> findPendingJobs()

    // Aktualizuje status zadania po pomyślnym przetworzeniu
    void markJobAsSuccess(String satelliteId)

    // Aktualizuje status zadania po nieudanym przetworzeniu
    void markJobAsFailed(String satelliteId)
}