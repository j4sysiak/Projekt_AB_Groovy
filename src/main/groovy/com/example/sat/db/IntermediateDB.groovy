package com.example.sat.db

import com.example.sat.dto.PhotoData

interface IntermediateDB {
    void insertPhoto(PhotoData photo)
    List<Map> getArchivedPhotos()
    int getPhotoCount()
    void clearPhotos()
}