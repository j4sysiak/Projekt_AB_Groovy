package com.example.sat.dto

import groovy.transform.Canonical

// To jest nasz JEDYNY, ujednolicony model misji.
@Canonical
class PhotoCollectionManifest {
    String missionId = "MCOL_" + System.nanoTime()
    List<PhotoTask> tasks

    // Możliwe statusy: PLANNED, ACCEPTED, UPLOADING, UPLOADED, COMPLETED, FAILED
    String status = "PLANNED" // Dodajemy pole statusu
}