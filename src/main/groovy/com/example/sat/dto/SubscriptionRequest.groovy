package com.example.sat.dto

import groovy.transform.Canonical


// To jest to, co klient będzie nam wysyłał, żeby zasubskrybować.
@Canonical
class SubscriptionRequest {
    String missionId       // Na jaką misję chcesz powiadomienie?
    String eventType       // Na jakie zdarzenie? Np. "MISSION_COMPLETED"
    String callbackUrl     // Pod jaki URL mamy wysłać powiadomienie?
}