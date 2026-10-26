package com.example.auto.dto
import groovy.transform.Canonical

@Canonical
class DownloadEvent {
    String eventType
    String autoId
    Map<String, Object> payload
    long timestamp = System.currentTimeMillis()
}