package com.example.auto.fakes
import com.example.auto.CommandModule

// Ulepszony, bardziej "inteligentny" ręczny mock
class ManualMockCommandModule implements CommandModule {

    // Możemy w teście ustawić, jak ma się zachowywać
    String thrusterHealth = "NOMINAL"
    double batteryLevel = 99.0

    @Override
    String sendCommand(String command, Map params = [:]) {
        // Symulujemy realistyczne odpowiedzi na komendy sprawdzające stan
        switch (command) {
            case "CHECK_POWER_STATUS":
                return "STATUS: Battery: ${batteryLevel}%, Panels: CHARGING"
            case "CHECK_SYSTEM_HEALTH":
                return "STATUS: Thrusters: ${thrusterHealth}"
            default:
                // Dla wszystkich innych komend zwracamy proste ACK
                return "ACK"
        }
    }
}