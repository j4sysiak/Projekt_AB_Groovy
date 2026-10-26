package com.example.auto.dto
import groovy.transform.Canonical

@Canonical
class TakePhotosRequest {
    int numberOfPhotos
    String quality = "HIGH"
    String authToken
}