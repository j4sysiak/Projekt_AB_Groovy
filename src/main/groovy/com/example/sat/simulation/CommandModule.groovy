// ścieżka: src/main/groovy/com/example/sat/simulation/CommandModule.groovy
package com.example.sat.simulation

/**
 * Kontrakt dla komponentów zdolnych do przyjmowania i wykonywania komend.
 */
interface CommandModule {
    String sendCommand(String command, Map params)
}