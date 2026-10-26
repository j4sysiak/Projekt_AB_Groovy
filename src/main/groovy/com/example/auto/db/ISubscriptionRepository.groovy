package com.example.auto.db

import com.example.auto.dto.SubscriptionRequest

interface ISubscriptionRepository {

    void saveSubscription(SubscriptionRequest subscription)

    List<SubscriptionRequest> findSubscriptionsByEvent(String eventType, String actionId)
}
