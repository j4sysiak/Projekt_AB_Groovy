package com.example.sat.dto

import groovy.transform.Canonical

// To jest nasz JEDYNY, ujednolicony model misji.
@Canonical
class PhotoCollectionManifest {
    String missionId = "MCOL_" + System.nanoTime()
    List<PhotoTask> tasks
    String status = "PLANNED" // Dodajemy pole statusu
}