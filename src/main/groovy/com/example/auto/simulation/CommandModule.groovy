// ścieżka: src/main/groovy/com/example/auto/simulation/CommandModule.groovy
package com.example.auto.simulation

/**
 * Kontrakt dla komponentów zdolnych do przyjmowania i wykonywania komend.
 */
interface CommandModule {
    String sendCommand(String command, Map params)
}