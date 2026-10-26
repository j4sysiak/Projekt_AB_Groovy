package com.example.auto.dto

import groovy.transform.Canonical

@Canonical
class PhotoTask {
     String taskId = "TASK_" + System.nanoTime()
     PhotoTaskStatus status = PhotoTaskStatus.PENDING
}