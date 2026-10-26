package com.example.auto

// Interfejs do zarządzania zadaniami reprocesowania
interface JobRepository {
    List<String> findPendingJobs()

    // Aktualizuje status zadania po pomyślnym przetworzeniu
    void markJobAsSuccess(String autoId)

    // Aktualizuje status zadania po nieudanym przetworzeniu
    void markJobAsFailed(String autoId)
}