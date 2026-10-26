package com.example.auto.dto

import groovy.transform.Canonical

@Canonical
class SubscriptionRequest {
    String actionId
    String eventType
    String callbackUrl
}