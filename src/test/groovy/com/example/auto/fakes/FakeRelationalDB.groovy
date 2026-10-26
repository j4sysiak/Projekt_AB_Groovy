package com.example.auto.fakes

import com.example.auto.db.IntermediateDB
import com.example.auto.dto.PhotoData
import com.example.auto.dto.SubscriptionRequest
import com.example.auto.db.RelationalDB

// Implementujemy interfejs

class FakeRelationalDB  implements RelationalDB, IntermediateDB  {

    private long nextId = 1
    final List<Map> photoTable = []

    // "TABELA" NA SUBSKRYPCJE
    //...trzymajmy listę obiektów! a nie mapę
    final List<SubscriptionRequest> subscriptionTable = [] // Lepszy typ!

    // === Metody z interfejsu RelationalDB ===

    @Override
    void insertPhoto(PhotoData photo) {
        def row = [
                id        : nextId++,
                photoId   : photo.photoId,
                actionId : photo.actionId,
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
    int getPhotoCountForAction(String actionId) {
        if (!actionId) return 0
        return photoTable.findAll { it.actionId == actionId }.size()
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
    List<SubscriptionRequest> findSubscriptionsByEvent(String eventType, String actionId) {
        return subscriptionTable.findAll {
            it.eventType == eventType && it.actionId == actionId
        }
    }

    @Override
    void clearSubscriptions() {
        subscriptionTable.clear()
    }
}