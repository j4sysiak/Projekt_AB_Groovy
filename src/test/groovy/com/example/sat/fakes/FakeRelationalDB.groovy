package com.example.sat.fakes

import com.example.sat.dto.PhotoData
import com.example.sat.db.ISubscriptionRepository
import com.example.sat.dto.SubscriptionRequest

// Implementujemy interfejs

class FakeRelationalDB  implements ISubscriptionRepository {
    // Symuluje tabelę 'photos'
    List<Map> photoTable = []

    private long nextId = 1

    // "TABELA" NA SUBSKRYPCJE
    final List<SubscriptionRequest> subscriptionTable = [] // ...trzymajmy listę obiektów! a nie mapę

    void insertPhoto(PhotoData photo) {
        def row = [
                id        : nextId++,
                photoId   : photo.photoId,
                missionId : photo.missionId, // <-- DODAJ TĘ LINIĘ!
                content   : photo.content,
                timestamp : photo.timestamp,
                quality   : photo.quality
        ]

        println "DATABASE: Inserting row into 'photos' table: ${row}"
        photoTable.add(row)
    }

    List<Map> getArchivedPhotos() {
        return new ArrayList<>(photoTable)
    }

    @Override
    void save(SubscriptionRequest subscription) { // Przyjmujemy obiekt
        subscriptionTable.add(subscription) // Dodajemy obiekt
        println "DB: New subscription added: ${subscription}"
    }

    @Override
    List<SubscriptionRequest> findByEventTypeAndMissionId(String eventType, String missionId) {
        // Teraz it to na pewno obiekt SubscriptionRequest, więc odwołanie do it.eventType jest bezpieczne
        return subscriptionTable.findAll {
            it.eventType == eventType && it.missionId == missionId
        }
    }
}