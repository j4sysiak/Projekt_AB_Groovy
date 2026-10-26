// ścieżka: src/main/groovy/com/example/auto/simulation/AutoConnection.groovy
package com.example.auto.simulation

/**
 * Kontrakt dla komponentów zarządzających podstawowym połączeniem.
 */
interface AutoConnection {
    void connect(String authToken)
    void disconnect()
    boolean isConnected()
    String fetchData(String autoIdId)
}