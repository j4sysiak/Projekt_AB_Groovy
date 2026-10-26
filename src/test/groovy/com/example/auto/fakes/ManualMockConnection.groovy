package com.example.auto.fakes
import com.example.auto.AutoConnection

class ManualMockConnection implements AutoConnection {
    String dataToReturn = "DEFAULT_DATA"
    boolean throwExceptionOnFetch = false

    @Override void connect(String authToken) {}
    @Override boolean isConnected() { return true }
    @Override
    String fetchData(String autoIdId) {
        if (throwExceptionOnFetch) throw new RuntimeException("Forced fetch error")
        return dataToReturn
    }
    @Override void disconnect() {}
}