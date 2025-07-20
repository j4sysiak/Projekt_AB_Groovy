package com.example.sat

interface SatelliteConnection {
    void connect(String authToken)
    boolean isConnected()
    String fetchData(String satelliteId)
    void disconnect()
}