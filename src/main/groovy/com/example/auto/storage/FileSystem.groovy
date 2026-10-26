package com.example.auto.storage

interface FileSystem {
    void saveFile(String name, String content)
    Map<String, String> readAllFiles()
    void deleteFile(String name)
    int getFileCount()
    void clear()
}