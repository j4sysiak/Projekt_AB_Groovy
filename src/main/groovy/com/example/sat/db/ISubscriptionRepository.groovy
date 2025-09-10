package com.example.sat.db

import com.example.sat.dto.SubscriptionRequest

interface ISubscriptionRepository {

    void saveSubscription(SubscriptionRequest subscription)

    List<SubscriptionRequest> findSubscriptionsByEvent(String eventType, String missionId)
}
