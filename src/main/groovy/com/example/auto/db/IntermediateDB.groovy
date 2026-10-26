package com.example.auto.db

import com.example.auto.dto.PhotoData

interface IntermediateDB {
    void insertPhoto(PhotoData photo)
    List<Map> getArchivedPhotos()
    int getPhotoCount()
    void clearPhotos()
}