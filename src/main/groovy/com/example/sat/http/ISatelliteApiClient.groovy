package com.example.sat.http

// Interfejs dla klienta, który komunikuje się z API satelity
interface ISatelliteApiClient {
    // Zdefiniujmy metody, których potrzebuje MissionUploader
    // Nazwa metody musi pasować do tego, co woła MissionUploaderImpl
    Map uploadMissionPlan(Object plan)
}