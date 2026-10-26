package com.example.auto

import groovy.transform.Canonical

@Canonical
class BatchReport {
    int totalTasks
    List<String> successfulIds = []
    Map<String, String> failedIds = [:] // Mapowanie ID na przyczynę błędu
}