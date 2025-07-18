package com.example.sat.fakes

class FakeFileSystem {
    // Klucz to nazwa pliku, wartość to zawartość (JSON jako string)
    Map<String, String> files = [:]

    void saveFile(String path, String content) {
        println "FILE_SYSTEM: Saving file to ${path}"
        files[path] = content
    }

    Map<String, String> readAllFiles() {
        return new HashMap<>(files) // Zwracamy kopię
    }

    void deleteFile(String path) {
        println "FILE_SYSTEM: Deleting file ${path}"
        files.remove(path)
    }
}