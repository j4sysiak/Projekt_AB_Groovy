package com.example.auto.dto

import groovy.transform.Canonical

@Canonical
class PhotoCollectionAction {
    String actionId = "MCOL_" + System.nanoTime()
    List<PhotoTask> tasks
    String status = "PLANNED"
}