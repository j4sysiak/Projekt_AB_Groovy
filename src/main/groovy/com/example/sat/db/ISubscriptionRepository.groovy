package com.example.sat.db

import com.example.sat.dto.SubscriptionRequest

interface ISubscriptionRepository {
    void save(SubscriptionRequest subscription) // Zmień sygnaturę w interfejsie
    List<Map> findByEventTypeAndMissionId(String eventType, String missionId)
}
