package com.example.sat.dto
import com.example.sat.Coordinates
import groovy.transform.Canonical

@Canonical
class MissionRequest {
    String missionType = "PHOTO_IMAGERY"
    Coordinates targetCoordinates
    long startTimeMillis
    long endTimeMillis
}