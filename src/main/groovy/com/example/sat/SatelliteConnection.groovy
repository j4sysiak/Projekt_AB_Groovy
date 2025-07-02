package com.example.sat

// Zmieniamy SatelliteConnection na interfejs
interface SatelliteConnection {
    void connect(String authToken)
    boolean isConnected()
    String fetchData(String satelliteId)
    void disconnect()
}