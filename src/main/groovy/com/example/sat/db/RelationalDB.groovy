package com.example.sat.db

import com.example.sat.dto.PhotoData
import com.example.sat.dto.SubscriptionRequest

interface RelationalDB extends ISubscriptionRepository {
    // Metody do obsługi zdjęć
    void insertPhoto(PhotoData photo)
    List<Map> getArchivedPhotos()
    int getPhotoCount()
    int getPhotoCountForMission(String missionId)
    void clearPhotos()

    // Metody do obsługi subskrypcji
    void saveSubscription(SubscriptionRequest subscription)
    List<SubscriptionRequest> findSubscriptionsByEvent(String eventType, String missionId)
    void clearSubscriptions()
}