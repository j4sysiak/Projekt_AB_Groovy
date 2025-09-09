// ścieżka: src/main/groovy/com/example/sat/simulation/SatelliteConnection.groovy
package com.example.sat.simulation

/**
 * Kontrakt dla komponentów zarządzających podstawowym połączeniem z satelitą.
 */
interface SatelliteConnection {
    void connect(String authToken)
    void disconnect()
    boolean isConnected()
    String fetchData(String satelliteId)
}