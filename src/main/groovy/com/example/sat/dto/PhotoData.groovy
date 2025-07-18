package com.example.sat.dto
import groovy.transform.Canonical

@Canonical
class PhotoData {
    String photoId = "IMG_" + System.nanoTime()
    long timestamp = System.currentTimeMillis()
    String quality
    String content // W symulacji to będzie base64, tutaj prosty string
}