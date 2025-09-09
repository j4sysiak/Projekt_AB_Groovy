package com.example.sat.fakes

import com.example.sat.db.IntermediateDB
import com.example.sat.dto.PhotoData
import com.example.sat.dto.SubscriptionRequest
import com.example.sat.db.RelationalDB

// Implementujemy interfejs

class FakeRelationalDB  implements RelationalDB, IntermediateDB  {

    private long nextId = 1
    final List<Map> photoTable = []

    // "TABELA" NA SUBSKRYPCJE
    //...trzymajmy listę obiektów! a nie mapę
    final List<SubscriptionRequest> subscriptionTable = [] // Lepszy typ!

    // === Metody z interfejsu RelationalDB ===

    // === Metody z interfejsu RelationalDB ===

    @Override
    void insertPhoto(PhotoData photo) {
        def row = [
                id        : nextId++,
                photoId   : photo.photoId,
                missionId : photo.missionId,
                timestamp : photo.timestamp,
                quality   : photo.quality,
                content   : photo.content
        ]
        println "DATABASE: Inserting row into 'photos' table: ${row}"
        photoTable.add(row)
    }

    @Override
    List<Map> getArchivedPhotos() {
        return new ArrayList<>(photoTable)
    }

    @Override
    int getPhotoCount() {
        return photoTable.size()
    }

    @Override
    int getPhotoCountForMission(String missionId) {
        if (!missionId) return 0
        return photoTable.findAll { it.missionId == missionId }.size()
    }

    @Override
    void clearPhotos() {
        photoTable.clear()
    }

    @Override
    void saveSubscription(SubscriptionRequest subscription) {
        subscriptionTable.add(subscription)
        println "DB: New subscription added: ${subscription}"
    }

    @Override
    List<SubscriptionRequest> findSubscriptionsByEvent(String eventType, String missionId) {
        return subscriptionTable.findAll {
            it.eventType == eventType && it.missionId == missionId
        }
    }

    @Override
    void clearSubscriptions() {
        subscriptionTable.clear()
    }
}