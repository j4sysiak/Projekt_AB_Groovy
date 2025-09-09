package com.example.sat.fakes

import com.example.sat.storage.FileSystem

class FakeFileSystem implements FileSystem{
    // Klucz to nazwa pliku, wartość to zawartość (JSON jako string)
    final Map<String, String> files = [:]

    @Override
    void saveFile(String path, String content) {
        println "FILE_SYSTEM: Saving file to ${path}"
        files[path] = content
    }

    @Override
    Map<String, String> readAllFiles() {
        return new HashMap<>(files) // Zwracamy kopię
    }

    @Override
    void deleteFile(String path) {
        println "FILE_SYSTEM: Deleting file ${path}"
        files.remove(path)
    }

    @Override
    int getFileCount() {
        return 0
    }

    @Override
    void clear() {

    }
}