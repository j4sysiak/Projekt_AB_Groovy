package com.example.sat.dto

import groovy.transform.Canonical

@Canonical
class PhotoCollectionManifest {
    String missionId = "MCOL_" + System.nanoTime()
    List<PhotoTask> tasks
}