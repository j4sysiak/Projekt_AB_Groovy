package com.example.auto.dto
import com.example.auto.domain.Coordinates
import groovy.transform.Canonical

@Canonical
class ActionRequest {
    String actionType = "PHOTO_IMAGERY"
    Coordinates targetCoordinates
    long startTimeMillis
    long endTimeMillis
}