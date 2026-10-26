package com.example.auto

interface DownloadModule {
    void sendEvent(String eventType, Map<String, Object> payload)
}