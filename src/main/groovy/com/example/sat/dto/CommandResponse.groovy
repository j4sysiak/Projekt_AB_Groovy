package com.example.sat.dto
import groovy.transform.Canonical

@Canonical
class CommandResponse {
    String status // np. "ACK" lub "NACK"
    String message
    double batteryLevel
}