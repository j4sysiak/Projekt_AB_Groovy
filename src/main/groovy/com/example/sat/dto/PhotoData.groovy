package com.example.sat.dto

import groovy.transform.Canonical
import groovy.transform.MapConstructor

@Canonical
@MapConstructor
class PhotoData {
    String photoId = "IMG_" + System.nanoTime()
    long timestamp = System.currentTimeMillis()
    String quality
    String content


    PhotoData(String quality, String content) {
        this.photoId = "IMG_" + System.nanoTime()
        this.timestamp = System.currentTimeMillis()
        this.quality = quality
        this.content = content
    }

    PhotoData(LinkedHashMap<String, Object> stringObjectLinkedHashMap) {}
}