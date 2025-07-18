package com.example.sat.fakes
import com.example.sat.SatelliteConnection

class ManualMockConnection implements SatelliteConnection {
    String dataToReturn = "DEFAULT_DATA"
    boolean throwExceptionOnFetch = false

    @Override void connect(String authToken) {}
    @Override boolean isConnected() { return true }
    @Override
    String fetchData(String satelliteId) {
        if (throwExceptionOnFetch) throw new RuntimeException("Forced fetch error")
        return dataToReturn
    }
    @Override void disconnect() {}
}