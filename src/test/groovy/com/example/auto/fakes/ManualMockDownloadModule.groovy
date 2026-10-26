package com.example.auto.fakes
import com.example.auto.DownloadModule

class ManualMockDownloadModule implements DownloadModule {
    @Override void sendEvent(String eventType, Map<String, Object> payload) {}
}