package com.example.auto.db

import com.example.auto.dto.PhotoData
import com.example.auto.dto.SubscriptionRequest

interface RelationalDB extends ISubscriptionRepository {
    void insertPhoto(PhotoData photo)
    List<Map> getArchivedPhotos()
    int getPhotoCount()
    int getPhotoCountForAction(String actionId)
    void clearPhotos()

    void saveSubscription(SubscriptionRequest subscription)
    List<SubscriptionRequest> findSubscriptionsByEvent(String eventType, String actionId)
    void clearSubscriptions()
}