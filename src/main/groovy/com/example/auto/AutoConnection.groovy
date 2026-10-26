package com.example.auto

interface AutoConnection {
    void connect(String authToken)
    boolean isConnected()
    String fetchData(String autoId)
    void disconnect()
}