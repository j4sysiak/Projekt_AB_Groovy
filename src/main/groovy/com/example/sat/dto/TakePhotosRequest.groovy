package com.example.sat.dto
import groovy.transform.Canonical

@Canonical
class TakePhotosRequest {
    int numberOfPhotos
    String quality = "HIGH" // Możemy określić jakość
    String authToken
}