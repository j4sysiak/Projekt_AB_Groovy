package com.example.auto.fakes

import com.example.auto.storage.FileSystem
import groovy.transform.Synchronized

/**
 * Testowa implementacja systemu plików, która jest w pełni bezpieczna wątkowo.
 * Używa @Synchronized na każdej metodzie, aby zapewnić, że wszystkie operacje
 * na mapie `storedFiles` są widoczne między wątkami.
 */
class FakeFileSystem implements FileSystem {

    private final Map<String, String> storedFiles = [:]

    @Synchronized
    @Override
    void saveFile(String name, String content) {
        println "FILE_SYSTEM: Saving file to ${name}"
        storedFiles[name] = content
    }

    @Synchronized
    @Override
    Map<String, String> readAllFiles() {
        return new LinkedHashMap<>(storedFiles)
    }

    @Synchronized
    @Override
    void deleteFile(String name) {
        storedFiles.remove(name)
    }

    @Synchronized
    @Override
    int getFileCount() {
        // Zwracamy RZECZYWISTY rozmiar mapy, a nie zahardkodowane 0.
        return storedFiles.size()
    }

    @Synchronized
    @Override
    void clear() {
        storedFiles.clear()
    }
}