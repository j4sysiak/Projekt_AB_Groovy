package com.example.sat.fakes

class FakeRelationalDB {
    // Symuluje tabelę 'photos'
    List<Map> photoTable = []
    private long nextId = 1

    void insertPhoto(Map photoRow) {
        photoRow.id = nextId++
        println "DATABASE: Inserting row into 'photos' table: ${photoRow}"
        photoTable.add(photoRow)
    }

    List<Map> getArchivedPhotos() {
        return new ArrayList<>(photoTable)
    }
}