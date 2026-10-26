package com.example.auto

import groovy.transform.Canonical

@Canonical
class DownloadModuleImpl implements DownloadModule {
    String downloadEndpoint // np. "udp://download.alpha.com:5555"

    @Override
    void sendEvent(String eventType, Map<String, Object> payload) {
        println "Download to [${downloadEndpoint}]: Event '${eventType}' with payload ${payload}"
    }
}