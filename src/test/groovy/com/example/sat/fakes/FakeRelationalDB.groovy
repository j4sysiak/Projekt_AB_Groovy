package com.example.sat.fakes

import com.example.sat.dto.PhotoData

class FakeRelationalDB {
    // Symuluje tabelę 'photos'
    List<Map> photoTable = []
    private long nextId = 1

    void insertPhoto(PhotoData photo) {
        def row = [
                id       : nextId++,
                photoId  : photo.photoId,
                timestamp: photo.timestamp,
                quality  : photo.quality,
                content  : photo.content
        ]
        println "DATABASE: Inserting row into 'photos' table: ${row}"
        photoTable.add(row)
    }

    List<Map> getArchivedPhotos() {
        return new ArrayList<>(photoTable)
    }
}